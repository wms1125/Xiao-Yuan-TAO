package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.payment.PayCommand;
import com.ruoyi.campus.payment.PayResult;
import com.ruoyi.campus.payment.RefundCommand;
import com.ruoyi.campus.payment.RefundResult;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.impl.CampusOrderServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：订单交易核心逻辑（防超卖 / 扣款 / 退款回滚 / 幂等 / 列表批量查询）。
 *
 * <p>补齐此前只有 Controller 层 Mockito 委派断言的缺口——把"AI 说它对了"变成"测试证明它对了"：
 * 下单扣库存、支付扣款与幂等、取消退款与回库存、超时取消的原子性分支、列表 N+1 回归。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 Service：订单交易核心逻辑")
class CampusOrderServiceTest {

    private static final long BUYER_ID = 2L;
    private static final long SELLER_ID = 3L;
    private static final long CART_ID = 10L;
    private static final long PRODUCT_ID = 100L;
    private static final String ORDER_NO = "CT202601010001";

    @Mock
    private CampusCartMapper cartMapper;
    @Mock
    private CampusProductMapper productMapper;
    @Mock
    private CampusOrderMapper orderMapper;
    @Mock
    private CampusOrderItemMapper orderItemMapper;
    @Mock
    private ICampusPaymentService paymentService;
    @Mock
    private ICampusCartService cartService;
    @Mock
    private ICampusNoticeService noticeService;

    @InjectMocks
    private CampusOrderServiceImpl orderService;

    @Captor
    private ArgumentCaptor<CampusOrder> orderCaptor;

    // ---------------- 下单 ----------------

    @Test
    @DisplayName("下单：购物车为空时拒绝")
    void createOrderRejectsEmptyCart() {
        when(cartService.selectListByUserId(BUYER_ID)).thenReturn(Collections.emptyList());

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.createOrder(BUYER_ID, "ry", null,
                "张三", "13800000000", "1号楼101", null, null));

        assertEquals("购物车为空，无法下单", ex.getMessage());
        verify(orderMapper, never()).insertCampusOrder(any());
    }

    @Test
    @DisplayName("下单：商品不存在时拒绝")
    void createOrderRejectsMissingProduct() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(cart(CART_ID, PRODUCT_ID, 1));
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.createOrder(BUYER_ID, "ry",
                new Long[] { CART_ID }, "张三", "13800000000", "1号楼101", null, null));

        assertTrue(ex.getMessage().contains("商品不存在"));
    }

    @Test
    @DisplayName("下单：商品未上架时拒绝")
    void createOrderRejectsOfflineProduct() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(cart(CART_ID, PRODUCT_ID, 1));
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, SELLER_ID, 0, "30.00"));

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.createOrder(BUYER_ID, "ry",
                new Long[] { CART_ID }, "张三", "13800000000", "1号楼101", null, null));

        assertTrue(ex.getMessage().contains("未上架"));
    }

    @Test
    @DisplayName("下单：不能购买自己发布的商品")
    void createOrderRejectsOwnProduct() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(cart(CART_ID, PRODUCT_ID, 1));
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, BUYER_ID, 1, "30.00"));

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.createOrder(BUYER_ID, "ry",
                new Long[] { CART_ID }, "张三", "13800000000", "1号楼101", null, null));

        assertTrue(ex.getMessage().contains("不能购买自己发布的商品"));
    }

    @Test
    @DisplayName("下单：原子扣库存影响行数为 0 时按库存不足拒绝（防超卖）")
    void createOrderRejectsOversell() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(cart(CART_ID, PRODUCT_ID, 5));
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, SELLER_ID, 1, "30.00"));
        when(productMapper.decreaseStock(PRODUCT_ID, 5)).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.createOrder(BUYER_ID, "ry",
                new Long[] { CART_ID }, "张三", "13800000000", "1号楼101", null, null));

        assertTrue(ex.getMessage().contains("库存不足"));
        // 关键：库存预占失败后不得落库订单，否则会留下"无货订单"
        verify(orderMapper, never()).insertCampusOrder(any());
        verify(cartMapper, never()).deleteCartByUserAndProduct(anyLong(), anyLong());
    }

    @Test
    @DisplayName("下单：成功时扣库存、落库、清购物车并推送通知")
    void createOrderSuccess() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(cart(CART_ID, PRODUCT_ID, 2));
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, SELLER_ID, 1, "30.00"));
        when(productMapper.decreaseStock(PRODUCT_ID, 2)).thenReturn(1);

        String orderNo = orderService.createOrder(BUYER_ID, "ry", new Long[] { CART_ID },
                "张三", "13800000000", "1号楼101", null, "尽快发货");

        assertTrue(orderNo.startsWith("CT"), "订单号应以 CT 开头");
        verify(productMapper).decreaseStock(PRODUCT_ID, 2);
        verify(orderMapper).insertCampusOrder(orderCaptor.capture());
        verify(orderItemMapper).insertCampusOrderItemBatch(anyList());
        verify(cartMapper).deleteCartByUserAndProduct(BUYER_ID, PRODUCT_ID);
        verify(noticeService).push(eq(BUYER_ID), eq("order"), anyString(), anyString(), eq(orderNo));

        CampusOrder saved = orderCaptor.getValue();
        assertEquals(Long.valueOf(BUYER_ID), saved.getUserId());
        assertEquals(Integer.valueOf(0), saved.getStatus(), "新订单应为待付款(0)");
        assertEquals(0, new BigDecimal("60.00").compareTo(saved.getTotalAmount()), "总金额应为 30.00 × 2 = 60.00");
    }

    @Test
    @DisplayName("下单：同一秒内并发下单，订单号互不重复（回归 uk_order_no 同秒冲突）")
    void createOrderGeneratesUniqueOrderNoUnderConcurrency() throws Exception {
        final int threads = 16;
        final int perThread = 8;
        when(cartMapper.selectCartById(CART_ID)).thenReturn(cart(CART_ID, PRODUCT_ID, 1));
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, SELLER_ID, 1, "30.00"));
        when(productMapper.decreaseStock(PRODUCT_ID, 1)).thenReturn(1);

        Set<String> orderNos = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        orderNos.add(orderService.createOrder(BUYER_ID, "ry", new Long[] { CART_ID },
                                "张三", "13800000000", "1号楼101", null, null));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads * perThread, orderNos.size(), "同一秒内并发下单的订单号必须互不重复");
    }

    @Test
    @DisplayName("下单：购物车项不属于本人时被忽略，最终因购物车为空而拒绝")
    void createOrderIgnoresOthersCartItem() {
        CampusCart others = cart(CART_ID, PRODUCT_ID, 1);
        others.setUserId(999L);
        when(cartMapper.selectCartById(CART_ID)).thenReturn(others);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.createOrder(BUYER_ID, "ry",
                new Long[] { CART_ID }, "张三", "13800000000", "1号楼101", null, null));

        assertEquals("购物车为空，无法下单", ex.getMessage());
    }

    // ---------------- 支付 ----------------

    @Test
    @DisplayName("支付：订单不属于本人时按不存在处理")
    void payOrderRejectsOthersOrder() {
        CampusOrder order = order(ORDER_NO, 999L, 0, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.payOrder(ORDER_NO, BUYER_ID));

        assertEquals("订单不存在", ex.getMessage());
        verify(paymentService, never()).pay(any(PayCommand.class));
    }

    @Test
    @DisplayName("支付：已支付订单幂等返回成功，不重复扣款")
    void payOrderIsIdempotent() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        assertTrue(orderService.payOrder(ORDER_NO, BUYER_ID));

        verify(paymentService, never()).pay(any(PayCommand.class));
        verify(orderMapper, never()).updateCampusOrder(any());
    }

    @Test
    @DisplayName("支付：非待付款状态拒绝")
    void payOrderRejectsWrongStatus() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 2, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.payOrder(ORDER_NO, BUYER_ID));

        assertEquals("订单状态异常，无法支付", ex.getMessage());
    }

    @Test
    @DisplayName("支付：余额不足返回 false 且订单状态不变")
    void payOrderReturnsFalseWhenBalanceInsufficient() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(paymentService.pay(any(PayCommand.class))).thenReturn(PayResult.insufficientBalance("余额不足"));

        assertFalse(orderService.payOrder(ORDER_NO, BUYER_ID));

        verify(orderMapper, never()).updateCampusOrder(any());
        ArgumentCaptor<PayCommand> cmdCaptor = ArgumentCaptor.forClass(PayCommand.class);
        verify(paymentService).pay(cmdCaptor.capture());
        PayCommand sent = cmdCaptor.getValue();
        assertEquals(BUYER_ID, sent.getUserId());
        assertEquals(ORDER_NO, sent.getOrderNo());
        assertEquals(0, new BigDecimal("60.00").compareTo(sent.getAmount()));
    }

    @Test
    @DisplayName("支付：成功时扣款并置为已付款，写入支付时间")
    void payOrderSuccess() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(paymentService.pay(any(PayCommand.class))).thenReturn(PayResult.success("TRADE-001"));

        assertTrue(orderService.payOrder(ORDER_NO, BUYER_ID));

        verify(orderMapper).updateCampusOrder(orderCaptor.capture());
        CampusOrder saved = orderCaptor.getValue();
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertNotNull(saved.getPayTime());
        ArgumentCaptor<PayCommand> cmdCaptor = ArgumentCaptor.forClass(PayCommand.class);
        verify(paymentService).pay(cmdCaptor.capture());
        assertEquals(ORDER_NO, cmdCaptor.getValue().getOrderNo());
    }

    @Test
    @DisplayName("支付：渠道失败时抛异常，不把订单静默留在待付款（避免钱已扣却显示未支付）")
    void payOrderThrowsWhenChannelFails() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(paymentService.pay(any(PayCommand.class))).thenReturn(PayResult.failed("渠道网关超时"));

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.payOrder(ORDER_NO, BUYER_ID));

        assertTrue(ex.getMessage().contains("支付失败"));
        verify(orderMapper, never()).updateCampusOrder(any());
    }

    // ---------------- 取消 ----------------

    @Test
    @DisplayName("取消：待付款订单仅回补库存，不产生退款")
    void cancelOrderWaitPayReturnsStockOnly() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Collections.singletonList(orderItem(PRODUCT_ID, 2)));

        assertTrue(orderService.cancelOrder(ORDER_NO, BUYER_ID));

        verify(productMapper).increaseStock(PRODUCT_ID, 2);
        verify(paymentService, never()).refund(any(RefundCommand.class));
        assertEquals(Integer.valueOf(4), order.getStatus());
        assertNotNull(order.getCancelTime());
    }

    @Test
    @DisplayName("取消：已付款订单退款并回补库存")
    void cancelOrderPaidRefundsAndReturnsStock() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Collections.singletonList(orderItem(PRODUCT_ID, 2)));
        when(paymentService.refund(any(RefundCommand.class))).thenReturn(RefundResult.success("REF-001"));

        assertTrue(orderService.cancelOrder(ORDER_NO, BUYER_ID));

        ArgumentCaptor<RefundCommand> cmdCaptor = ArgumentCaptor.forClass(RefundCommand.class);
        verify(paymentService).refund(cmdCaptor.capture());
        RefundCommand sent = cmdCaptor.getValue();
        assertEquals(BUYER_ID, sent.getUserId());
        assertEquals(ORDER_NO, sent.getOrderNo());
        assertEquals(0, new BigDecimal("60.00").compareTo(sent.getAmount()));
        verify(productMapper).increaseStock(PRODUCT_ID, 2);
        assertEquals(Integer.valueOf(4), order.getStatus());
    }

    @Test
    @DisplayName("取消：退款失败时抛异常中止，不回补库存（避免钱没退、库存已回）")
    void cancelOrderAbortsWhenRefundFails() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        // 注意：此处**不** stub orderItemMapper——退款失败发生在查订单明细之前，
        // 若 stub 了反而说明生产代码先准备了回库存（顺序错误）。
        when(paymentService.refund(any(RefundCommand.class))).thenReturn(RefundResult.failed("渠道超时"));

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.cancelOrder(ORDER_NO, BUYER_ID));

        assertTrue(ex.getMessage().contains("退款失败"));
        verify(productMapper, never()).increaseStock(anyLong(), anyInt());
        verify(orderMapper, never()).updateCampusOrder(any());
        assertEquals(Integer.valueOf(1), order.getStatus());
    }

    @Test
    @DisplayName("取消：已发货订单不可取消")
    void cancelOrderRejectsShipped() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 2, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.cancelOrder(ORDER_NO, BUYER_ID));

        assertEquals("当前订单状态不可取消", ex.getMessage());
        verify(paymentService, never()).refund(any(RefundCommand.class));
    }

    // ---------------- 发货 / 收货 ----------------

    @Test
    @DisplayName("发货：非已付款订单拒绝")
    void shipOrderRejectsNotPaid() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.shipOrder(ORDER_NO, "SF001", SELLER_ID));

        assertEquals("订单状态异常，无法发货", ex.getMessage());
    }

    @Test
    @DisplayName("发货：卖家本人置为已发货、物流单号去空格并向买家推送通知")
    void shipOrderSuccess() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.updateCampusOrder(any())).thenReturn(1);

        assertEquals(1, orderService.shipOrder(ORDER_NO, "  SF001  ", SELLER_ID));

        assertEquals(Integer.valueOf(2), order.getStatus());
        assertEquals("SF001", order.getLogisticsNo());
        assertNotNull(order.getShipTime());
        verify(noticeService).push(eq(BUYER_ID), eq("ship"), anyString(), anyString(), eq(ORDER_NO));
    }

    @Test
    @DisplayName("发货：非卖家本人且非管理员拒绝（越权负向）")
    void shipOrderRejectsNonSeller() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        // 买家自己点发货：既不是卖家也不是管理员，归属校验必须拒绝
        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.shipOrder(ORDER_NO, "SF001", BUYER_ID));

        assertEquals("仅订单卖家本人或管理员可发货", ex.getMessage());
        verify(orderMapper, never()).updateCampusOrder(any());
        verify(noticeService, never()).push(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("发货：管理员(admin)可代任意订单发货")
    void shipOrderAllowsAdmin() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.updateCampusOrder(any())).thenReturn(1);

        // 管理员 id=1，非本单卖家，仍可代发
        assertEquals(1, orderService.shipOrder(ORDER_NO, "SF002", 1L));
    }

    @Test
    @DisplayName("确认收货：非已发货订单拒绝")
    void finishOrderRejectsNotShipped() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 1, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.finishOrder(ORDER_NO, BUYER_ID));

        assertEquals("订单状态异常，无法确认收货", ex.getMessage());
    }

    @Test
    @DisplayName("确认收货：置为已完成并写入完成时间")
    void finishOrderSuccess() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 2, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.updateCampusOrder(any())).thenReturn(1);

        assertEquals(1, orderService.finishOrder(ORDER_NO, BUYER_ID));

        assertEquals(Integer.valueOf(3), order.getStatus());
        assertNotNull(order.getFinishTime());
    }

    // ---------------- 超时自动取消 ----------------

    @Test
    @DisplayName("超时取消：超时阈值非正数时直接返回 0，不查库")
    void autoCancelSkipsWhenTimeoutNotPositive() {
        assertEquals(0, orderService.autoCancelTimeoutOrder(0));

        verify(orderMapper, never()).selectTimeoutWaitPayOrders(any());
    }

    @Test
    @DisplayName("超时取消：原子更新影响行数为 0 时跳过，不回补库存（并发已被支付/取消）")
    void autoCancelSkipsWhenAtomicUpdateMisses() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectTimeoutWaitPayOrders(any())).thenReturn(Collections.singletonList(order));
        when(orderMapper.cancelWaitPayOrderIfStillWaitPay(eq(ORDER_NO), any())).thenReturn(0);

        assertEquals(0, orderService.autoCancelTimeoutOrder(30));

        verify(productMapper, never()).increaseStock(anyLong(), anyInt());
        verify(noticeService, never()).push(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("超时取消：原子更新生效时回补库存并推送通知")
    void autoCancelReturnsStockAndNotifies() {
        CampusOrder order = order(ORDER_NO, BUYER_ID, 0, "60.00");
        when(orderMapper.selectTimeoutWaitPayOrders(any())).thenReturn(Collections.singletonList(order));
        when(orderMapper.cancelWaitPayOrderIfStillWaitPay(eq(ORDER_NO), any())).thenReturn(1);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Collections.singletonList(orderItem(PRODUCT_ID, 3)));

        assertEquals(1, orderService.autoCancelTimeoutOrder(30));

        verify(productMapper).increaseStock(PRODUCT_ID, 3);
        verify(noticeService).push(eq(BUYER_ID), eq("order"), anyString(), anyString(), eq(ORDER_NO));
    }

    // ---------------- 列表（N+1 回归） ----------------

    @Test
    @DisplayName("列表：订单项走批量查询，不再逐单查询（N+1 回归）")
    void selectListUsesBatchItemQuery() {
        CampusOrder first = order("CT001", BUYER_ID, 1, "10.00");
        CampusOrder second = order("CT002", BUYER_ID, 3, "20.00");
        when(orderMapper.selectOrderList(any())).thenReturn(Arrays.asList(first, second));
        CampusOrderItem itemA = orderItem(PRODUCT_ID, 1);
        itemA.setOrderNo("CT001");
        CampusOrderItem itemB = orderItem(200L, 2);
        itemB.setOrderNo("CT002");
        when(orderItemMapper.selectByOrderNos(anyList())).thenReturn(Arrays.asList(itemA, itemB));

        List<CampusOrder> result = orderService.selectList(new CampusOrder());

        verify(orderItemMapper).selectByOrderNos(anyList());
        verify(orderItemMapper, never()).selectByOrderNo(anyString());
        assertEquals(1, result.get(0).getItems().size());
        assertEquals(Long.valueOf(200L), result.get(1).getItems().get(0).getProductId());
    }

    @Test
    @DisplayName("列表：结果为空时不发起订单项查询")
    void selectListSkipsItemQueryWhenEmpty() {
        when(orderMapper.selectOrderList(any())).thenReturn(Collections.emptyList());

        assertTrue(orderService.selectList(new CampusOrder()).isEmpty());

        verify(orderItemMapper, never()).selectByOrderNos(anyList());
    }

    @Test
    @DisplayName("列表：聚合统计直接委派 Mapper（供概览使用）")
    void countGroupByStatusDelegates() {
        orderService.countGroupByStatus(new CampusOrder());

        verify(orderMapper, times(1)).countGroupByStatus(any());
    }

    // ---------------- 夹具 ----------------

    private CampusCart cart(Long cartId, Long productId, int quantity) {
        CampusCart c = new CampusCart();
        c.setId(cartId);
        c.setUserId(BUYER_ID);
        c.setProductId(productId);
        c.setQuantity(quantity);
        return c;
    }

    private CampusProduct product(Long id, Long sellerId, int status, String price) {
        CampusProduct p = new CampusProduct();
        p.setId(id);
        p.setTitle("商品" + id);
        p.setSellerId(sellerId);
        p.setStatus(status);
        p.setPrice(new BigDecimal(price));
        p.setImages("/campus-img/books.jpg");
        return p;
    }

    private CampusOrder order(String orderNo, Long userId, int status, String totalAmount) {
        CampusOrder o = new CampusOrder();
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal(totalAmount));
        return o;
    }

    private CampusOrderItem orderItem(Long productId, int quantity) {
        CampusOrderItem item = new CampusOrderItem();
        item.setOrderNo(ORDER_NO);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
