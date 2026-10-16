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
        aftersaleMapper.insertCampusAftersale(as);
        // 订单置为退款中
        order.setStatus(5);
        orderMapper.updateCampusOrder(order);
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
            order.setStatus(STATUS_FINISHED); // 驳回后视为已完成
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
