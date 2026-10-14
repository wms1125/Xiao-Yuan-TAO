package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.ICampusAccountService;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.campus.service.ICampusOrderService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CampusOrderServiceImpl implements ICampusOrderService {

    @Autowired
    private CampusCartMapper cartMapper;
    @Autowired
    private CampusProductMapper productMapper;
    @Autowired
    private CampusOrderMapper orderMapper;
    @Autowired
    private CampusOrderItemMapper orderItemMapper;
    @Autowired
    private ICampusAccountService accountService;
    @Autowired
    private ICampusCartService cartService;

    /** 订单状态常量 */
    public static final int STATUS_WAIT_PAY = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_SHIPPED = 2;
    public static final int STATUS_FINISHED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_REFUNDING = 5;
    public static final int STATUS_REFUNDED = 6;

    private String genOrderNo(Long userId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return "CT" + sdf.format(new Date()) + userId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, String userName, Long[] cartIds,
                               String receiverName, String receiverPhone, String address, String remark) {
        // 1) 取购物车明细（指定 cartIds，否则取已勾选）
        List<CampusCart> carts;
        if (cartIds != null && cartIds.length > 0) {
            carts = new ArrayList<>();
            for (Long cid : cartIds) {
                CampusCart c = cartMapper.selectCartById(cid);
                if (c != null && c.getUserId().equals(userId)) {
                    carts.add(c);
                }
            }
        } else {
            carts = cartService.selectListByUserId(userId);
        }
        if (carts.isEmpty()) {
            throw new ServiceException("购物车为空，无法下单");
        }

        // 2) 逐条校验库存并预占（M3-5 防超卖：decreaseStock 带 WHERE stock>=count）
        CampusOrder order = new CampusOrder();
        order.setOrderNo(genOrderNo(userId));
        order.setUserId(userId);
        order.setUserName(userName);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setAddress(address);
        order.setRemark(remark);
        order.setStatus(STATUS_WAIT_PAY);
        order.setTotalAmount(BigDecimal.ZERO);

        List<CampusOrderItem> items = new ArrayList<>();
        for (CampusCart c : carts) {
            CampusProduct p = productMapper.selectCampusProductById(c.getProductId());
            if (p == null) {
                throw new ServiceException("商品不存在(id=" + c.getProductId() + ")");
            }
            if (p.getStatus() == null || p.getStatus() != 1) {
                throw new ServiceException("商品[" + p.getTitle() + "]未上架");
            }
            if (p.getSellerId() != null && p.getSellerId().equals(userId)) {
                throw new ServiceException("不能购买自己发布的商品[" + p.getTitle() + "]");
            }
            int qty = c.getQuantity() == null ? 1 : c.getQuantity();
            // 预占库存：影响行数=0 表示库存不足（乐观锁保护）
            int aff = productMapper.decreaseStock(p.getId(), qty);
            if (aff == 0) {
                throw new ServiceException("商品[" + p.getTitle() + "]库存不足");
            }
            // 订单项
            CampusOrderItem item = new CampusOrderItem();
            item.setProductId(p.getId());
            item.setProductTitle(p.getTitle());
            item.setImage(p.getImages());
            item.setPrice(p.getPrice());
            item.setQuantity(qty);
            item.setSubtotal(p.getPrice().multiply(BigDecimal.valueOf(qty)));
            items.add(item);
            order.setTotalAmount(order.getTotalAmount().add(item.getSubtotal()));
        }

        // 3) 落库订单 + 订单项 + 清购物车
        orderMapper.insertCampusOrder(order);
        for (CampusOrderItem item : items) {
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
        }
        orderItemMapper.insertCampusOrderItemBatch(items);
        // 清空已下单的购物车记录
        for (CampusCart c : carts) {
            cartMapper.deleteCartByUserAndProduct(userId, c.getProductId());
        }
        return order.getOrderNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(String orderNo, Long userId) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() == STATUS_PAID) {
            return true; // 已支付，幂等返回成功（防止重复支付）
        }
        if (order.getStatus() != STATUS_WAIT_PAY) {
            throw new ServiceException("订单状态异常，无法支付");
        }
        // 扣沙箱余额（库存已在下单时预占）
        boolean ok = accountService.pay(userId, order.getTotalAmount());
        if (!ok) {
            return false; // 余额不足
        }
        order.setStatus(STATUS_PAID);
        order.setPayTime(new Date());
        orderMapper.updateCampusOrder(order);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(String orderNo, Long userId) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() == STATUS_WAIT_PAY) {
            // 未付款：仅回库存
            returnStock(order);
            order.setStatus(STATUS_CANCELLED);
            order.setCancelTime(new Date());
            orderMapper.updateCampusOrder(order);
            return true;
        } else if (order.getStatus() == STATUS_PAID) {
            // 已付款：退款 + 回库存
            returnStock(order);
            accountService.refund(userId, order.getTotalAmount());
            order.setStatus(STATUS_CANCELLED);
            order.setCancelTime(new Date());
            orderMapper.updateCampusOrder(order);
            return true;
        }
        throw new ServiceException("当前订单状态不可取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int shipOrder(String orderNo) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || order.getStatus() != STATUS_PAID) {
            throw new ServiceException("订单状态异常，无法发货");
        }
        order.setStatus(STATUS_SHIPPED);
        order.setShipTime(new Date());
        return orderMapper.updateCampusOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finishOrder(String orderNo, Long userId) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() != STATUS_SHIPPED) {
            throw new ServiceException("订单状态异常，无法确认收货");
        }
        order.setStatus(STATUS_FINISHED);
        order.setFinishTime(new Date());
        return orderMapper.updateCampusOrder(order);
    }

    /** 回补库存 */
    private void returnStock(CampusOrder order) {
        List<CampusOrderItem> items = orderItemMapper.selectByOrderNo(order.getOrderNo());
        for (CampusOrderItem item : items) {
            productMapper.increaseStock(item.getProductId(), item.getQuantity());
        }
    }

    @Override
    public CampusOrder selectWithItems(String orderNo) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order != null) {
            order.setItems(orderItemMapper.selectByOrderNo(orderNo));
        }
        return order;
    }

    @Override
    public CampusOrder selectWithItemsById(Long id) {
        CampusOrder order = orderMapper.selectOrderById(id);
        if (order != null) {
            order.setItems(orderItemMapper.selectByOrderId(id));
        }
        return order;
    }

    @Override
    public List<CampusOrder> selectList(CampusOrder order) {
        List<CampusOrder> list = orderMapper.selectOrderList(order);
        for (CampusOrder o : list) {
            o.setItems(orderItemMapper.selectByOrderNo(o.getOrderNo()));
        }
        return list;
    }

    @Override
    public int countByUserId(Long userId) {
        return orderMapper.countByUserId(userId);
    }
}
