package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.domain.CampusUserPayQr;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.mapper.CampusUserPayQrMapper;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.service.ICampusAddressService;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.impl.CampusOrderServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * C2C 卖家收款码直付 Service 层测试（V8）。
 *
 * <p>覆盖直付链路的四条硬约束：
 * <ol>
 *   <li><b>单卖家</b>：跨卖家订单（购物车混购多个卖家的商品）必须拒绝，一个收款码只对应一个人；</li>
 *   <li><b>卖家必须有码</b>：未上传收款码时明确拒绝，而非展示空图；</li>
 *   <li><b>状态原子性</b>：markAwaitSellerConfirm/confirmPayIfAwaitConfirm 影响 0 行必须显式报错，
 *       不能静默成功；</li>
 *   <li><b>越权确认</b>：只有订单 seller_id 本人能确认收款。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · C2C 卖家收款码直付 Service")
class CampusDirectPayServiceTest {

    private static final long BUYER_ID = 2L;
    private static final long SELLER_ID = 10L;
    private static final String ORDER_NO = "CT2026101812000001";
    private static final long PRODUCT_A = 100L;
    private static final long PRODUCT_B = 200L;

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
    @Mock
    private ICampusAddressService addressService;
    @Mock
    private CampusUserPayQrMapper payQrMapper;

    @InjectMocks
    private CampusOrderServiceImpl orderService;

    private CampusOrder waitPayOrder() {
        CampusOrder order = new CampusOrder();
        order.setOrderNo(ORDER_NO);
        order.setUserId(BUYER_ID);
        order.setTotalAmount(new BigDecimal("82.00"));
        order.setStatus(CampusOrderServiceImpl.STATUS_WAIT_PAY);
        return order;
    }

    private CampusOrderItem item(long productId, String title) {
        CampusOrderItem item = new CampusOrderItem();
        item.setOrderNo(ORDER_NO);
        item.setProductId(productId);
        item.setProductTitle(title);
        item.setQuantity(1);
        return item;
    }

    private CampusProduct product(long productId, Long sellerId) {
        CampusProduct p = new CampusProduct();
        p.setId(productId);
        p.setTitle("商品" + productId);
        p.setSellerId(sellerId);
        p.setStatus(1);
        return p;
    }

    private CampusUserPayQr qrOf(Long userId) {
        CampusUserPayQr qr = new CampusUserPayQr();
        qr.setUserId(userId);
        qr.setQrUrl("/profile/upload/payqr/wechat-qr-" + userId + ".png");
        return qr;
    }

    // ---------------- initiateDirectPay ----------------

    @Test
    @DisplayName("直付发起：单卖家且有码，订单 0→7 并通知卖家")
    void initiateMarksAwaitConfirmAndNotifiesSeller() {
        CampusOrder order = waitPayOrder();
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Collections.singletonList(item(PRODUCT_A, "iPad")));
        when(productMapper.selectCampusProductById(PRODUCT_A)).thenReturn(product(PRODUCT_A, SELLER_ID));
        when(payQrMapper.selectByUserId(SELLER_ID)).thenReturn(qrOf(SELLER_ID));
        when(orderMapper.markAwaitSellerConfirm(eq(ORDER_NO), anyString(), eq(SELLER_ID))).thenReturn(1);

        orderService.initiateDirectPay(ORDER_NO, BUYER_ID, "");

        ArgumentCaptor<String> proofCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderMapper).markAwaitSellerConfirm(eq(ORDER_NO), proofCaptor.capture(), eq(SELLER_ID));
        assertEquals("", proofCaptor.getValue()); // 空截图规整为空串入库（列 NOT NULL DEFAULT ''）
        verify(noticeService).push(eq(SELLER_ID), eq("order"), anyString(), anyString(), eq(ORDER_NO));
    }

    @Test
    @DisplayName("直付发起：跨卖家订单被拒绝，不落任何状态")
    void initiateRejectsMultiSellerOrder() {
        CampusOrder order = waitPayOrder();
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Arrays.asList(item(PRODUCT_A, "iPad"), item(PRODUCT_B, "吉他")));
        when(productMapper.selectCampusProductById(PRODUCT_A)).thenReturn(product(PRODUCT_A, SELLER_ID));
        when(productMapper.selectCampusProductById(PRODUCT_B)).thenReturn(product(PRODUCT_B, 99L));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.initiateDirectPay(ORDER_NO, BUYER_ID, null));
        assertTrue(ex.getMessage().contains("跨卖家"));
        verify(orderMapper, never()).markAwaitSellerConfirm(anyString(), anyString(), any());
        verify(noticeService, never()).push(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("直付发起：卖家未上传收款码被明确拒绝")
    void initiateRejectsWhenSellerHasNoQr() {
        CampusOrder order = waitPayOrder();
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Collections.singletonList(item(PRODUCT_A, "iPad")));
        when(productMapper.selectCampusProductById(PRODUCT_A)).thenReturn(product(PRODUCT_A, SELLER_ID));
        when(payQrMapper.selectByUserId(SELLER_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.initiateDirectPay(ORDER_NO, BUYER_ID, null));
        assertTrue(ex.getMessage().contains("收款码"));
        verify(orderMapper, never()).markAwaitSellerConfirm(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("直付发起：状态 7 重复提交幂等，不再写库")
    void initiateIsIdempotentAtStatus7() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_AWAIT_SELLER_CONFIRM);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        orderService.initiateDirectPay(ORDER_NO, BUYER_ID, null);

        verify(orderMapper, never()).markAwaitSellerConfirm(anyString(), anyString(), any());
        verify(noticeService, never()).push(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("直付发起：已付款/已取消等非 0 状态拒绝")
    void initiateRejectsNonWaitPayStatus() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_PAID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        assertThrows(ServiceException.class,
                () -> orderService.initiateDirectPay(ORDER_NO, BUYER_ID, null));
        verify(orderMapper, never()).markAwaitSellerConfirm(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("直付发起：非订单归属人不可发起")
    void initiateRejectsNonOwner() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(waitPayOrder());

        assertThrows(ServiceException.class,
                () -> orderService.initiateDirectPay(ORDER_NO, 999L, null));
        verify(orderMapper, never()).markAwaitSellerConfirm(anyString(), anyString(), any());
    }

    // ---------------- confirmDirectPay ----------------

    @Test
    @DisplayName("直付确认：卖家本人确认，7→1 原子生效并通知买家")
    void confirmTransitionsToPaid() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_AWAIT_SELLER_CONFIRM);
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.confirmPayIfAwaitConfirm(eq(ORDER_NO), any(Date.class), any(Date.class), anyLong())).thenReturn(1);

        orderService.confirmDirectPay(ORDER_NO, SELLER_ID);

        verify(orderMapper).confirmPayIfAwaitConfirm(eq(ORDER_NO), any(Date.class), any(Date.class), anyLong());
        verify(noticeService).push(eq(BUYER_ID), eq("order"), anyString(), anyString(), eq(ORDER_NO));
    }

    @Test
    @DisplayName("直付确认：非卖家本人（买家冒充）被拒绝")
    void confirmRejectsNonSeller() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_AWAIT_SELLER_CONFIRM);
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.confirmDirectPay(ORDER_NO, BUYER_ID));
        assertTrue(ex.getMessage().contains("卖家本人"));
        verify(orderMapper, never()).confirmPayIfAwaitConfirm(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("直付确认：原子更新 0 行（已被并发取消）显式报错，不静默成功")
    void confirmFailsWhenAtomicUpdateMisses() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_AWAIT_SELLER_CONFIRM);
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.confirmPayIfAwaitConfirm(eq(ORDER_NO), any(Date.class), any(Date.class), anyLong())).thenReturn(0);

        assertThrows(ServiceException.class, () -> orderService.confirmDirectPay(ORDER_NO, SELLER_ID));
        verify(noticeService, never()).push(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("直付确认：订单不处于待确认状态（如仍待付款）拒绝")
    void confirmRejectsWrongStatus() {
        CampusOrder order = waitPayOrder();
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        assertThrows(ServiceException.class, () -> orderService.confirmDirectPay(ORDER_NO, SELLER_ID));
        verify(orderMapper, never()).confirmPayIfAwaitConfirm(anyString(), any(), any(), any());
    }

    // ---------------- cancelOrder（状态 7 分支） ----------------

    @Test
    @DisplayName("直付撤回：待卖家确认时买家取消，回库存且不触发任何退款动作")
    void cancelAtAwaitConfirmReleasesStockWithoutRefund() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_AWAIT_SELLER_CONFIRM);
        order.setSellerId(SELLER_ID);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(Collections.singletonList(item(PRODUCT_A, "iPad")));

        boolean ok = orderService.cancelOrder(ORDER_NO, BUYER_ID);

        assertTrue(ok);
        verify(productMapper).increaseStock(PRODUCT_A, 1);
        verify(orderMapper).updateCampusOrder(any(CampusOrder.class));
        // 平台不经手资金：直付订单取消绝不能走沙箱退款通道
        verify(paymentService, never()).refund(any());
    }

    // ---------------- selectWithItems 附加收款码 ----------------

    @Test
    @DisplayName("详情附带：待付款单卖家订单返回卖家收款码，供买家扫码")
    void detailAttachesSellerQrForWaitPayOrder() {
        CampusOrder order = waitPayOrder();
        List<CampusOrderItem> items = Collections.singletonList(item(PRODUCT_A, "iPad"));
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO)).thenReturn(items);
        when(productMapper.selectCampusProductById(PRODUCT_A)).thenReturn(product(PRODUCT_A, SELLER_ID));
        when(payQrMapper.selectByUserId(SELLER_ID)).thenReturn(qrOf(SELLER_ID));

        CampusOrder result = orderService.selectWithItems(ORDER_NO);

        assertEquals("/profile/upload/payqr/wechat-qr-" + SELLER_ID + ".png", result.getSellerPayQrUrl());
    }

    @Test
    @DisplayName("详情附带：非待付款状态不附带收款码（已发货订单无需展示）")
    void detailSkipsQrForOtherStatus() {
        CampusOrder order = waitPayOrder();
        order.setStatus(CampusOrderServiceImpl.STATUS_SHIPPED);
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO)).thenReturn(Collections.emptyList());

        CampusOrder result = orderService.selectWithItems(ORDER_NO);

        assertNull(result.getSellerPayQrUrl());
        verify(payQrMapper, never()).selectByUserId(any());
    }
}
