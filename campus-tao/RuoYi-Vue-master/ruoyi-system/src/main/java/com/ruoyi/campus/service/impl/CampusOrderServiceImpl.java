package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusAddress;
import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.ICampusAccountService;
import com.ruoyi.campus.service.ICampusAddressService;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.ICampusOrderService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    @Autowired
    private ICampusNoticeService noticeService;
    @Autowired
    private ICampusAddressService addressService;

    /** 订单状态常量 */
    public static final int STATUS_WAIT_PAY = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_SHIPPED = 2;
    public static final int STATUS_FINISHED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_REFUNDING = 5;
    public static final int STATUS_REFUNDED = 6;

    /**
     * 同秒内下单的递增序号。
     *
     * <p>原实现为「CT + 秒级时间戳 + userId」——同一用户在同一秒内重复下单会生成相同订单号，
     * 直接撞 {@code uk_order_no} 且无重试。改为「CT + 秒级时间戳 + 4 位原子序号」：
     * 单实例内每秒可容纳 10000 笔订单，且不再依赖 userId（不同用户同秒也不会相撞）。
     *
     * <p>边界（已知、未隐藏）：多实例部署时各 JVM 的序号相互独立，理论上仍可能在同秒相撞，
     * 由 {@code uk_order_no} 唯一键兜底。本项目为单实例部署，如需水平扩展应改为数据库序列。
     */
    private static final AtomicInteger ORDER_NO_SEQ = new AtomicInteger(0);

    private String genOrderNo(Long userId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        int seq = ORDER_NO_SEQ.getAndUpdate(v -> (v + 1) % 10000);
        return "CT" + sdf.format(new Date()) + String.format("%04d", seq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, String userName, Long[] cartIds,
                               String receiverName, String receiverPhone, String address, Long addressId, String remark) {
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

        // 1.1) 选用地址簿时以地址簿内容为准（归属校验在 service 内完成），
        //      防止前端篡改；未选用则按手填内容下单（兼容原有调用方）。
        String snapshot = address;
        if (addressId != null) {
            CampusAddress chosen = addressService.requireOwned(addressId, userId);
            receiverName = chosen.getReceiver();
            receiverPhone = chosen.getPhone();
            address = chosen.fullAddress();
            snapshot = chosen.getReceiver() + " " + chosen.getPhone() + " " + chosen.fullAddress();
        }

        // 2) 逐条校验库存并预占（M3-5 防超卖：decreaseStock 带 WHERE stock>=count）
        CampusOrder order = new CampusOrder();
        order.setOrderNo(genOrderNo(userId));
        order.setUserId(userId);
        order.setUserName(userName);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setAddress(address);
        order.setAddressSnapshot(snapshot == null ? "" : snapshot);
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
        // 站内通知：下单提醒（含未支付将自动取消的提示）
        noticeService.push(userId, "order", "订单创建成功",
                "订单 " + order.getOrderNo() + " 已创建，应付 ¥" + order.getTotalAmount()
                        + "，请尽快完成支付，超时将自动取消。", order.getOrderNo());
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
    public int shipOrder(String orderNo, String logisticsNo) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || order.getStatus() != STATUS_PAID) {
            throw new ServiceException("订单状态异常，无法发货");
        }
        order.setStatus(STATUS_SHIPPED);
        order.setShipTime(new Date());
        order.setLogisticsNo(logisticsNo == null ? "" : logisticsNo.trim());
        int rows = orderMapper.updateCampusOrder(order);
        // 站内通知：发货提醒（含物流单号）
        String suffix = order.getLogisticsNo().isEmpty() ? "" : "，物流单号：" + order.getLogisticsNo();
        noticeService.push(order.getUserId(), "ship", "订单已发货",
                "订单 " + orderNo + " 已发货" + suffix + "，请注意查收。", orderNo);
        return rows;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoCancelTimeoutOrder(int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            return 0;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -timeoutMinutes);
        List<CampusOrder> timeouts = orderMapper.selectTimeoutWaitPayOrders(calendar.getTime());
        int cancelled = 0;
        Date now = new Date();
        for (CampusOrder order : timeouts) {
            // 原子取消：仅当仍为待付款时才生效；已被用户支付/取消的订单影响行数为 0，跳过
            int affected = orderMapper.cancelWaitPayOrderIfStillWaitPay(order.getOrderNo(), now);
            if (affected == 0) {
                continue;
            }
            returnStock(order);
            cancelled++;
            noticeService.push(order.getUserId(), "order", "订单已自动取消",
                    "订单 " + order.getOrderNo() + " 因超时未支付已被自动取消，占用库存已释放。",
                    order.getOrderNo());
        }
        return cancelled;
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
        if (list.isEmpty()) {
            return list;
        }
        // 一次批量取回整页订单项再按订单号分组，替代原先"逐单查询"的 N+1
        List<String> orderNos = list.stream().map(CampusOrder::getOrderNo).collect(Collectors.toList());
        Map<String, List<CampusOrderItem>> itemsByOrderNo = orderItemMapper.selectByOrderNos(orderNos).stream()
                .collect(Collectors.groupingBy(CampusOrderItem::getOrderNo));
        for (CampusOrder o : list) {
            o.setItems(itemsByOrderNo.getOrDefault(o.getOrderNo(), new ArrayList<>()));
        }
        return list;
    }

    @Override
    public List<CampusStatusStat> countGroupByStatus(CampusOrder order) {
        return orderMapper.countGroupByStatus(order);
    }

    @Override
    public int countByUserId(Long userId) {
        return orderMapper.countByUserId(userId);
    }
}
