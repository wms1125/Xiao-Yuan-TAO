package com.ruoyi.campus.payment.impl;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusPaymentRecord;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusPaymentRecordMapper;
import com.ruoyi.campus.observability.CampusMetrics;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.payment.IPaymentGateway;
import com.ruoyi.campus.payment.PayCommand;
import com.ruoyi.campus.payment.PayNotify;
import com.ruoyi.campus.payment.PayResult;
import com.ruoyi.campus.payment.PaymentChannelProperties;
import com.ruoyi.campus.payment.PaymentGatewayResolver;
import com.ruoyi.campus.payment.RefundCommand;
import com.ruoyi.campus.payment.RefundResult;
import com.ruoyi.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 支付业务门面实现（校园淘）
 *
 * @author campus-tao
 */
@Service
public class CampusPaymentServiceImpl implements ICampusPaymentService {

    /** 订单状态常量（与 CampusOrderServiceImpl 保持一致，避免跨包引用内部常量） */
    private static final int STATUS_WAIT_PAY = 0;
    private static final int STATUS_PAID = 1;

    /** 渠道不可解析时的打标值，与 {@code CampusPaymentController.MODE_UNAVAILABLE} 同义 */
    private static final String MODE_UNAVAILABLE = "unavailable";

    /** 流水交易类型：支付 */
    private static final String TRADE_PAY = "PAY";
    /** 流水交易类型：退款 */
    private static final String TRADE_REFUND = "REFUND";

    private static final Logger log = LoggerFactory.getLogger(CampusPaymentServiceImpl.class);

    private final PaymentGatewayResolver resolver;
    private final PaymentChannelProperties props;
    private final CampusOrderMapper orderMapper;
    private final CampusMetrics metrics;
    private final CampusPaymentRecordMapper paymentRecordMapper;

    /**
     * 构造器注入：三个依赖里有两者（resolver / props）不是可 mock 的简单协作者，
     * 显式构造使"渠道路由失败"与"回调幂等"这两类行为可在单测里直接装配验证。
     */
    @Autowired
    public CampusPaymentServiceImpl(PaymentGatewayResolver resolver,
                                    PaymentChannelProperties props,
                                    CampusOrderMapper orderMapper,
                                    CampusMetrics metrics,
                                    CampusPaymentRecordMapper paymentRecordMapper) {
        this.resolver = resolver;
        this.props = props;
        this.orderMapper = orderMapper;
        this.metrics = metrics;
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @Override
    public PayResult pay(PayCommand cmd) {
        IPaymentGateway gateway;
        try {
            gateway = resolver.resolve();
        } catch (RuntimeException e) {
            // 渠道解析失败（未注册/未启用）同样计入失败：这是"用户根本付不出钱"的最直接原因。
            // 若只在拿到网关之后打点，最该告警的场景反而没有任何数据。
            metrics.payment(String.valueOf(props.getChannel()), MODE_UNAVAILABLE, false);
            throw e;
        }
        boolean success = false;
        try {
            PayResult result = gateway.pay(cmd);
            success = result.isSuccess();
            // 落支付流水：保留渠道交易号供对账；沙箱扣款不经手卖家资金，故 sellerId 记空。
            recordPayment(cmd.getOrderNo(), cmd.getUserId(), null, cmd.getAmount(),
                    TRADE_PAY, gateway.channel(), result.getChannelTradeNo(),
                    success, success ? null : result.getFailReason());
            return result;
        } finally {
            // finally 打点：渠道抛异常时也计为 failed，否则"渠道挂了"在指标上是空白
            metrics.payment(gateway.channel(), gateway.mode(), success);
        }
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        IPaymentGateway gateway;
        try {
            gateway = resolver.resolve();
        } catch (RuntimeException e) {
            metrics.refund(String.valueOf(props.getChannel()), MODE_UNAVAILABLE, false);
            throw e;
        }
        boolean success = false;
        try {
            RefundResult result = gateway.refund(cmd);
            success = result.isSuccess();
            // 退款流水补全卖家：从订单反查，便于按卖家对账退款去向。
            CampusOrder order = orderMapper.selectOrderByOrderNo(cmd.getOrderNo());
            recordPayment(cmd.getOrderNo(), cmd.getUserId(),
                    order == null ? null : order.getSellerId(), cmd.getAmount(),
                    TRADE_REFUND, gateway.channel(),
                    result.getRefundNo() != null ? result.getRefundNo() : cmd.getChannelTradeNo(),
                    success, success ? null : result.getFailReason());
            return result;
        } finally {
            metrics.refund(gateway.channel(), gateway.mode(), success);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaidNotify(String channel, PayNotify notify) {
        if (!notify.isSignValid()) {
            throw new ServiceException("回调验签失败，已拒绝处理");
        }
        if (!notify.isPaid()) {
            return false;
        }
        CampusOrder order = orderMapper.selectOrderByOrderNo(notify.getOrderNo());
        if (order == null) {
            throw new ServiceException("回调对应的订单不存在: " + notify.getOrderNo());
        }
        // 幂等闸门：已支付/已推进的订单直接放过，渠道重试推送不会重复改状态
        if (order.getStatus() != STATUS_WAIT_PAY) {
            return false;
        }
        // 金额核对：渠道告知金额与订单金额不一致时拒绝，防篡改金额的攻击
        BigDecimal expected = order.getTotalAmount();
        if (notify.getAmount() != null && expected != null
                && notify.getAmount().compareTo(expected) != 0) {
            throw new ServiceException("回调金额与订单金额不一致，已拒绝处理");
        }
        order.setStatus(STATUS_PAID);
        order.setPayTime(new Date());
        orderMapper.updateCampusOrder(order);
        // 渠道回调流水：以回调携带的交易号落库，是后续对账最权威的凭证。
        recordPayment(notify.getOrderNo(), order.getUserId(), order.getSellerId(),
                notify.getAmount() != null ? notify.getAmount() : expected,
                TRADE_PAY, channel, notify.getChannelTradeNo(), true, "渠道回调");
        return true;
    }

    /**
     * 落一条支付/退款流水。
     *
     * <p>流水写入失败不应阻断主交易链路（支付/退款已由渠道侧完成，抛异常会让用户
     * 看到"支付失败"但钱已扣），故此处吞掉异常并留下告警日志。</p>
     */
    private void recordPayment(String orderNo, Long userId, Long sellerId, BigDecimal amount,
                               String tradeType, String channel, String channelTradeNo,
                               boolean success, String remark) {
        try {
            CampusPaymentRecord record = new CampusPaymentRecord();
            record.setOrderNo(orderNo);
            record.setUserId(userId);
            record.setSellerId(sellerId);
            record.setAmount(amount);
            record.setTradeType(tradeType);
            record.setChannel(channel);
            record.setChannelTradeNo(channelTradeNo);
            record.setStatus(success ? "SUCCESS" : "FAIL");
            record.setRemark(remark);
            record.setCreateTime(new Date());
            paymentRecordMapper.insertCampusPaymentRecord(record);
        } catch (RuntimeException e) {
            log.warn("支付流水写入失败 orderNo={} tradeType={} channelTradeNo={}",
                    orderNo, tradeType, channelTradeNo, e);
        }
    }

    @Override
    public String currentChannel() {
        return props.getChannel();
    }
}
