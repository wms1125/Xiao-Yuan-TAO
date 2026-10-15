package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusOrder;

import java.util.List;

/**
 * 订单服务（校园淘）
 * 状态机：0待付款 → 1已付款 → 2已发货 → 3已完成；4已取消；5退款中；6已退款
 */
public interface ICampusOrderService {

    /** 结算下单：预占库存，生成订单与订单项，清空对应购物车 */
    String createOrder(Long userId, String userName, Long[] cartIds,
                        String receiverName, String receiverPhone, String address, String remark);

    /** 支付：扣沙箱余额，订单 0→1。余额不足返回 false */
    boolean payOrder(String orderNo, Long userId);

    /** 取消：未付款回库存(状态→4)；已付款退款+回库存(状态→4) */
    boolean cancelOrder(String orderNo, Long userId);

    /** 发货（管理端）：1→2，可录入物流单号 */
    int shipOrder(String orderNo, String logisticsNo);

    /** 确认收货（买家）：2→3 */
    int finishOrder(String orderNo, Long userId);

    /**
     * 运营：自动取消超过 timeoutMinutes 仍未支付的订单并回补库存。
     * 原子更新（status=0 才置 4），并发下不会误取消已支付订单。
     * @return 实际取消的订单数
     */
    int autoCancelTimeoutOrder(int timeoutMinutes);

    /** 带订单项的详情 */
    CampusOrder selectWithItems(String orderNo);

    /** 根据ID带订单项 */
    CampusOrder selectWithItemsById(Long id);

    List<CampusOrder> selectList(CampusOrder order);

    int countByUserId(Long userId);
}
