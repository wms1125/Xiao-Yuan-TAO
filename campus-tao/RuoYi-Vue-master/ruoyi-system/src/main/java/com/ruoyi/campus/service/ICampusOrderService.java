package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.vo.CampusStatusStat;

import java.util.List;

/**
 * 订单服务（校园淘）
 * 状态机：0待付款 → 1已付款 → 2已发货 → 3已完成；4已取消；5退款中；6已退款；
 * 7待卖家确认收款（C2C 直付中间态：0 --买家已扫码付款--> 7 --卖家确认--> 1）
 */
public interface ICampusOrderService {

    /** 结算下单：预占库存，生成订单与订单项，清空对应购物车
     *  @param addressId 可选的收货地址簿 ID；传入时以地址簿内容为准并写入地址快照 */
    String createOrder(Long userId, String userName, Long[] cartIds,
                        String receiverName, String receiverPhone, String address, Long addressId, String remark);

    /** 支付：扣沙箱余额，订单 0→1。余额不足返回 false */
    boolean payOrder(String orderNo, Long userId);

    /**
     * C2C 直付：买家声明已扫码付款给卖家，订单 0→7（待卖家确认收款）。
     * 校验：订单归属、全部订单项同属一个卖家、卖家已上传收款码。
     * @param payProofUrl 买家可选上传的付款截图
     */
    void initiateDirectPay(String orderNo, Long userId, String payProofUrl);

    /**
     * C2C 直付：卖家确认已收到买家转账，订单 7→1（写 pay_time）。
     * 平台不经手资金，此动作仅是状态确认；仅订单 seller_id 本人可操作。
     */
    void confirmDirectPay(String orderNo, Long sellerId);

    /** 取消：未付款/待卖家确认回库存(状态→4)；已付款退款+回库存(状态→4) */
    boolean cancelOrder(String orderNo, Long userId);

    /**
     * 发货：1→2，可录入物流单号。C2C 模式下由**订单卖家本人**发货，
     * 管理员可代任意订单发货；二者之外一律拒绝（归属校验在 service 内完成）。
     * @param operatorId 操作者（取自登录态），用于归属校验
     */
    int shipOrder(String orderNo, String logisticsNo, Long operatorId);

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

    /** 列表页概览：按状态分组统计（与 selectList 使用同一筛选条件） */
    List<CampusStatusStat> countGroupByStatus(CampusOrder order);

    int countByUserId(Long userId);
}
