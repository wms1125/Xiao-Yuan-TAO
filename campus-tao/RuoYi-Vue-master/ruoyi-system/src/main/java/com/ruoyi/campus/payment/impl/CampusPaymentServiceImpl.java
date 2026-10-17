package com.ruoyi.campus.payment.impl;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.mapper.CampusOrderMapper;
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

    private final PaymentGatewayResolver resolver;
    private final PaymentChannelProperties props;
    private final CampusOrderMapper orderMapper;
    private final CampusMetrics metrics;

    /**
     * 构造器注入：三个依赖里有两者（resolver / props）不是可 mock 的简单协作者，
     * 显式构造使"渠道路由失败"与"回调幂等"这两类行为可在单测里直接装配验证。
     */
    @Autowired
    public CampusPaymentServiceImpl(PaymentGatewayResolver resolver,
                                    PaymentChannelProperties props,
                                    CampusOrderMapper orderMapper,
                                    CampusMetrics metrics) {
        this.resolver = resolver;
        this.props = props;
        this.orderMapper = orderMapper;
        this.metrics = metrics;
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
        // 注：渠道交易号（channelTradeNo）当前无落库位置。对账需要它，但加列要走 V3 迁移，
        // 不塞进本任务的范围。待办见 CHANGELOG「未纳入本轮」。
        orderMapper.updateCampusOrder(order);
        return true;
    }

    @Override
    public String currentChannel() {
        return props.getChannel();
    }
}
