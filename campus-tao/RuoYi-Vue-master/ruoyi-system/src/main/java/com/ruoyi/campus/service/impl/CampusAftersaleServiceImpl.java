package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
import com.ruoyi.campus.mapper.CampusAftersaleMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.payment.RefundCommand;
import com.ruoyi.campus.payment.RefundResult;
import com.ruoyi.campus.service.ICampusAftersaleService;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.ICampusOrderService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class CampusAftersaleServiceImpl implements ICampusAftersaleService {

    private static final int STATUS_SHIPPED = 2;
    private static final int STATUS_FINISHED = 3;

    @Autowired
    private CampusAftersaleMapper aftersaleMapper;
    @Autowired
    private CampusOrderMapper orderMapper;
    @Autowired
    private CampusOrderItemMapper orderItemMapper;
    @Autowired
    private CampusProductMapper productMapper;
    @Autowired
    private ICampusPaymentService paymentService;
    @Autowired
    private ICampusNoticeService noticeService;
    @Autowired
    private ICampusOrderService orderService;

    private String genAftersaleNo(Long userId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return "AS" + sdf.format(new Date()) + userId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String apply(Long userId, String userName, String orderNo, Integer type, String reason) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() != STATUS_SHIPPED && order.getStatus() != STATUS_FINISHED) {
            throw new ServiceException("订单未发货，暂不能申请售后");
        }
        CampusAftersale as = new CampusAftersale();
        as.setAftersaleNo(genAftersaleNo(userId));
        as.setOrderId(order.getId());
        as.setOrderNo(orderNo);
        as.setUserId(userId);
        as.setUserName(userName);
        as.setType(type == null ? Integer.valueOf(1) : type);
        as.setReason(reason);
        as.setRefundAmount(order.getTotalAmount());
        as.setStatus(0);
        // 记录申请前的订单状态：驳回时据此还原，避免"已发货"被强行跳成"已完成"
        as.setOrderStatusBefore(order.getStatus());
        aftersaleMapper.insertCampusAftersale(as);
        // 订单置为退款中
        order.setStatus(5);
        orderMapper.updateCampusOrder(order);
        // 站内通知：告知卖家买家提交了售后申请
        for (Long sid : orderService.resolveSellerIds(orderNo)) {
            noticeService.push(sid, "aftersale", "买家提交了售后申请",
                    "订单 " + orderNo + " 买家提交了售后申请，金额 ¥" + order.getTotalAmount()
                            + "，请关注平台处理结果。", as.getAftersaleNo());
        }
        return as.getAftersaleNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean audit(String aftersaleNo, String handler, boolean pass, String remark) {
        CampusAftersale as = aftersaleMapper.selectByAftersaleNo(aftersaleNo);
        if (as == null || as.getStatus() != 0) {
            throw new ServiceException("售后单状态异常");
        }
        CampusOrder order = orderMapper.selectOrderByOrderNo(as.getOrderNo());
        if (order == null) {
            // 售后单存在但关联订单缺失属数据一致性问题：显式失败并整体回滚，
            // 否则后续 order.setStatus(...) 会抛 NPE 并可能已先执行退款/回库存造成半成品状态。
            throw new ServiceException("关联订单不存在，无法审核");
        }
        if (pass) {
            // 退款走支付网关；失败必须抛异常使整笔审核回滚，
            // 否则会出现"售后单已通过、钱没退、库存已回"的三不一致状态
            RefundResult refundResult = paymentService.refund(RefundCommand.of(
                    as.getUserId(), as.getOrderNo(), null, as.getRefundAmount()));
            if (!refundResult.isSuccess()) {
                throw new ServiceException("退款失败：" + refundResult.getFailReason());
            }
            // 回库存
            List<CampusOrderItem> items = orderItemMapper.selectByOrderNo(as.getOrderNo());
            for (CampusOrderItem item : items) {
                productMapper.increaseStock(item.getProductId(), item.getQuantity());
            }
            as.setStatus(3);
            order.setStatus(6); // 已退款
        } else {
            as.setStatus(2);
            // 驳回后还原申请前的订单状态：原来"已发货(2)"就回到 2，不再强行置"已完成(3)"。
            // 历史数据无 order_status_before 时保守回退为已完成(3)。
            Integer before = as.getOrderStatusBefore() != null ? as.getOrderStatusBefore() : STATUS_FINISHED;
            order.setStatus(before);
            // 状态一致性：已完成必须有完成时间，其它状态不应残留完成时间。
            if (before == STATUS_FINISHED) {
                if (order.getFinishTime() == null) {
                    order.setFinishTime(new Date());
                }
            }
        }
        as.setHandler(handler);
        as.setHandleRemark(remark);
        as.setHandleTime(new Date());
        aftersaleMapper.updateCampusAftersale(as);
        orderMapper.updateCampusOrder(order);
        // 站内通知：审核结果（通过 = 已退款；驳回 = 已关闭）
        if (pass) {
            noticeService.push(as.getUserId(), "aftersale", "售后审核通过",
                    "售后单 " + aftersaleNo + " 已审核通过，退款 ¥" + as.getRefundAmount() + " 已退回沙箱余额。",
                    aftersaleNo);
        } else {
            noticeService.push(as.getUserId(), "aftersale", "售后申请被驳回",
                    "售后单 " + aftersaleNo + " 未通过审核" + (remark == null || remark.isEmpty() ? "。" : "，原因：" + remark),
                    aftersaleNo);
        }
        return true;
    }

    @Override
    public CampusAftersale selectByAftersaleNo(String aftersaleNo) {
        return aftersaleMapper.selectByAftersaleNo(aftersaleNo);
    }

    @Override
    public List<CampusAftersale> selectList(CampusAftersale aftersale) {
        return aftersaleMapper.selectAftersaleList(aftersale);
    }

    @Override
    public List<CampusStatusStat> countGroupByStatus(CampusAftersale aftersale) {
        return aftersaleMapper.countGroupByStatus(aftersale);
    }
}
