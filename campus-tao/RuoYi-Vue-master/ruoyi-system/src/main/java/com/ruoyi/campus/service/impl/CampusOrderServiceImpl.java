package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusAddress;
import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
import com.ruoyi.campus.domain.CampusUserPayQr;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.mapper.CampusUserPayQrMapper;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.payment.PayCommand;
import com.ruoyi.campus.payment.PayResult;
import com.ruoyi.campus.payment.RefundCommand;
import com.ruoyi.campus.payment.RefundResult;
import com.ruoyi.campus.service.ICampusAddressService;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.ICampusOrderService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private ICampusPaymentService paymentService;
    @Autowired
    private ICampusCartService cartService;
    @Autowired
    private ICampusNoticeService noticeService;
    @Autowired
    private ICampusAddressService addressService;
    @Autowired
    private CampusUserPayQrMapper payQrMapper;

    /** 订单状态常量 */
    public static final int STATUS_WAIT_PAY = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_SHIPPED = 2;
    public static final int STATUS_FINISHED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_REFUNDING = 5;
    public static final int STATUS_REFUNDED = 6;
    /** C2C 直付中间态：买家已扫码付款，待卖家确认收款（V8） */
    public static final int STATUS_AWAIT_SELLER_CONFIRM = 7;

    /** 付款截图 URL 上限（与 pay_proof_url 列宽一致） */
    private static final int PAY_PROOF_URL_MAX = 500;

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
        // 站内通知：告知卖家有新订单
        for (Long sid : resolveSellerIds(order.getOrderNo())) {
            noticeService.push(sid, "order", "您有一笔新订单",
                    "订单 " + order.getOrderNo() + " 已下单，金额 ¥" + order.getTotalAmount()
                            + "，等待买家付款。", order.getOrderNo());
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
        // 扣款走支付网关（默认沙箱渠道扣站内余额；接真实渠道后此处代码不变）
        // 余额不足与渠道失败分开处理：前者订单保持待付款并提示充值，
        // 后者抛异常触发事务回滚，避免"钱已扣、订单却没动"的半完成状态。
        PayResult payResult = paymentService.pay(
                PayCommand.of(userId, order.getOrderNo(), order.getTotalAmount()));
        if (payResult.getStatus() == PayResult.Status.INSUFFICIENT_BALANCE) {
            return false; // 余额不足
        }
        if (!payResult.isSuccess()) {
            throw new ServiceException("支付失败：" + payResult.getFailReason());
        }
        order.setStatus(STATUS_PAID);
        order.setPayTime(new Date());
        orderMapper.updateCampusOrder(order);
        // 站内通知：支付成功告知买家，并提醒卖家发货
        noticeService.push(userId, "order", "支付成功",
                "订单 " + orderNo + " 已支付 ¥" + order.getTotalAmount() + "，请等待卖家发货。", orderNo);
        for (Long sid : resolveSellerIds(orderNo)) {
            noticeService.push(sid, "order", "订单已付款",
                    "订单 " + orderNo + " 买家已付款 ¥" + order.getTotalAmount() + "，请尽快发货。", orderNo);
        }
        return true;
    }

    /**
     * C2C 直付：买家声明已扫码付款给卖家，订单 0→7。
     *
     * <p>三道前置校验缺一不可：
     * <ol>
     *   <li><b>单卖家</b>：一个收款码只能对应一个人，跨卖家订单（购物车混购）无法直付；</li>
     *   <li><b>卖家已上传收款码</b>：没有码就没有支付标的，必须明确拒绝而非展示空图；</li>
     *   <li><b>原子状态推进</b>：markAwaitSellerConfirm 带 status=0 条件，
     *       与沙箱支付/取消/超时取消并发时只有一方生效。</li>
     * </ol>
     *
     * <p>平台不经手资金：本方法不产生任何金额变动，仅做状态撮合。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initiateDirectPay(String orderNo, Long userId, String payProofUrl) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() == STATUS_AWAIT_SELLER_CONFIRM) {
            return; // 已发起过（买家重复点击），幂等返回
        }
        if (order.getStatus() != STATUS_WAIT_PAY) {
            throw new ServiceException("订单状态异常，无法发起收款码直付");
        }
        String proof = payProofUrl == null ? "" : payProofUrl.trim();
        if (proof.length() > PAY_PROOF_URL_MAX) {
            throw new ServiceException("付款截图地址过长");
        }
        // 1) 单卖家校验：以商品表 sellerId 为准，逐项核对
        Long sellerId = null;
        for (CampusOrderItem item : orderItemMapper.selectByOrderNo(orderNo)) {
            CampusProduct p = productMapper.selectCampusProductById(item.getProductId());
            if (p == null || p.getSellerId() == null || p.getSellerId() <= 0) {
                throw new ServiceException("商品[" + item.getProductTitle() + "]缺少有效卖家信息，无法直付");
            }
            if (sellerId == null) {
                sellerId = p.getSellerId();
            } else if (!sellerId.equals(p.getSellerId())) {
                throw new ServiceException("跨卖家订单暂不支持收款码直付，请分开下单");
            }
        }
        // 2) 卖家必须已上传收款码
        CampusUserPayQr qr = payQrMapper.selectByUserId(sellerId);
        if (qr == null) {
            throw new ServiceException("卖家尚未上传收款码，暂无法直付，可使用沙箱支付");
        }
        // 3) 原子推进 0→7（并发下与沙箱支付/取消只有一方生效）
        int affected = orderMapper.markAwaitSellerConfirm(orderNo, proof, sellerId);
        if (affected == 0) {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        noticeService.push(sellerId, "order", "买家已扫码付款",
                "订单 " + orderNo + " 买家已扫码付款 ¥" + order.getTotalAmount()
                        + "，请核对本人收款后点击「确认已收款」。",
                orderNo);
    }

    /**
     * C2C 直付：卖家确认已收到买家转账，订单 7→1。
     *
     * <p>确认动作只允许订单 seller_id 本人执行；confirmPayIfAwaitConfirm 带 status=7
     * 条件，与超时取消并发时不会把已取消订单「复活」为已付款。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDirectPay(String orderNo, Long sellerId) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() != STATUS_AWAIT_SELLER_CONFIRM) {
            throw new ServiceException("订单不处于待确认收款状态");
        }
        if (order.getSellerId() == null || !order.getSellerId().equals(sellerId)) {
            throw new ServiceException("仅订单卖家本人可确认收款");
        }
        Date now = new Date();
        int affected = orderMapper.confirmPayIfAwaitConfirm(orderNo, now, now, sellerId);
        if (affected == 0) {
            throw new ServiceException("订单状态已变化（可能已被取消），请刷新后查看");
        }
        noticeService.push(order.getUserId(), "order", "卖家已确认收款",
                "订单 " + orderNo + " 卖家已确认收到您的转账，订单已支付，等待发货。",
                orderNo);
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
            notifySellerCancelled(order);
            return true;
        } else if (order.getStatus() == STATUS_AWAIT_SELLER_CONFIRM) {
            // 待卖家确认（C2C 直付）：买家撤回。
            // 钱已由买家直接付给卖家、平台不经手，退款只能由双方线下协商；
            // 此处仅做状态回退与库存释放，不产生任何资金动作。
            returnStock(order);
            order.setStatus(STATUS_CANCELLED);
            order.setCancelTime(new Date());
            orderMapper.updateCampusOrder(order);
            notifySellerCancelled(order);
            return true;
        } else if (order.getStatus() == STATUS_PAID) {
            // 已付款：退款 + 回库存。
            // 顺序必须是**先退款、后回库存**：退款是不可逆的外部动作，
            // 若先回库存再退款，退款失败时只能靠 @Transactional 回滚来撤销库存增量——
            // 一旦将来 returnStock 被标注 REQUIRES_NEW（或事务被移除），
            // 就会留下"库存已回补、钱却没退"的不一致。先退款则失败时天然无副作用需要撤销。
            RefundResult refundResult = paymentService.refund(RefundCommand.of(
                    userId, order.getOrderNo(), null, order.getTotalAmount()));
            if (!refundResult.isSuccess()) {
                throw new ServiceException("退款失败：" + refundResult.getFailReason());
            }
            returnStock(order);
            order.setStatus(STATUS_CANCELLED);
            order.setCancelTime(new Date());
            orderMapper.updateCampusOrder(order);
            notifySellerCancelled(order);
            return true;
        }
        throw new ServiceException("当前订单状态不可取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int shipOrder(String orderNo, String logisticsNo, Long operatorId) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order == null || order.getStatus() != STATUS_PAID) {
            throw new ServiceException("订单状态异常，无法发货");
        }
        // 归属校验：C2C 直付下由卖家本人发货，管理员可代发；
        // 二者之外（如买家、其他卖家）一律拒绝，前端隐藏不等于后端放行。
        boolean isSeller = operatorId != null && operatorId.equals(order.getSellerId());
        if (!isSeller && !SecurityUtils.isAdmin(operatorId)) {
            throw new ServiceException("仅订单卖家本人或管理员可发货");
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
        int rows = orderMapper.updateCampusOrder(order);
        // 站内通知：告知卖家买家已确认收货
        for (Long sid : resolveSellerIds(orderNo)) {
            noticeService.push(sid, "order", "买家已确认收货",
                    "订单 " + orderNo + " 买家已确认收货，交易完成。", orderNo);
        }
        return rows;
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
                    "订单 " + order.getOrderNo() + " 因超时未完成支付确认已被自动取消，占用库存已释放。",
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

    /**
     * 解析订单涉及的卖家 ID 集合（去重，排除空值、无效值(<=0)与买家本人），
     * 供下单/支付/收货/取消等业务事件通知卖家。跨卖家订单会返回多个卖家。
     */
    @Override
    public List<Long> resolveSellerIds(String orderNo) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        Long buyerId = order == null ? null : order.getUserId();
        Set<Long> sellers = new LinkedHashSet<>();
        for (CampusOrderItem item : orderItemMapper.selectByOrderNo(orderNo)) {
            CampusProduct p = productMapper.selectCampusProductById(item.getProductId());
            if (p != null && p.getSellerId() != null && p.getSellerId() > 0
                    && !p.getSellerId().equals(buyerId)) {
                sellers.add(p.getSellerId());
            }
        }
        return new ArrayList<>(sellers);
    }

    /** 站内通知：买家取消订单后告知相关卖家 */
    private void notifySellerCancelled(CampusOrder order) {
        for (Long sid : resolveSellerIds(order.getOrderNo())) {
            noticeService.push(sid, "order", "买家已取消订单",
                    "订单 " + order.getOrderNo() + " 已被买家取消，占用库存已释放。", order.getOrderNo());
        }
    }

    @Override
    public CampusOrder selectWithItems(String orderNo) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        if (order != null) {
            order.setItems(orderItemMapper.selectByOrderNo(orderNo));
            attachSellerPayQr(order);
        }
        return order;
    }

    /**
     * 待付款(0)/待卖家确认(7)的订单详情附带卖家收款码，供买家扫码直付。
     * 仅当订单项同属一个卖家且收款码已上传时附带；其他情形静默返回原样——
     * 附带失败不应挡住详情查看。
     */
    private void attachSellerPayQr(CampusOrder order) {
        Integer st = order.getStatus();
        if (st == null || (st != STATUS_WAIT_PAY && st != STATUS_AWAIT_SELLER_CONFIRM)) {
            return;
        }
        Long sellerId = order.getSellerId();
        if (sellerId == null && order.getItems() != null && !order.getItems().isEmpty()) {
            for (CampusOrderItem item : order.getItems()) {
                CampusProduct p = productMapper.selectCampusProductById(item.getProductId());
                if (p == null || p.getSellerId() == null || p.getSellerId() <= 0) {
                    return;
                }
                if (sellerId == null) {
                    sellerId = p.getSellerId();
                } else if (!sellerId.equals(p.getSellerId())) {
                    return; // 跨卖家订单不展示收款码
                }
            }
        }
        if (sellerId == null || sellerId.equals(order.getUserId())) {
            return;
        }
        CampusUserPayQr qr = payQrMapper.selectByUserId(sellerId);
        if (qr != null) {
            order.setSellerPayQrUrl(qr.getQrUrl());
        }
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
