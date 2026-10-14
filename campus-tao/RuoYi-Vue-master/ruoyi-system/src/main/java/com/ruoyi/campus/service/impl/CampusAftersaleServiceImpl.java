package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.mapper.CampusAftersaleMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.ICampusAccountService;
import com.ruoyi.campus.service.ICampusAftersaleService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private ICampusAccountService accountService;

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
        as.setType(type == null ? 1 : type);
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
        if (pass) {
            // 退款到沙箱余额 + 回库存
            accountService.refund(as.getUserId(), as.getRefundAmount());
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
}
