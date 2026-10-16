package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.mapper.CampusAftersaleMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.payment.RefundCommand;
import com.ruoyi.campus.payment.RefundResult;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.impl.CampusAftersaleServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：售后申请与审核（退款 + 回库存 + 数据一致性防御）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 Service：售后审核核心逻辑")
class CampusAftersaleServiceTest {

    private static final long BUYER_ID = 2L;
    private static final long PRODUCT_ID = 100L;
    private static final String ORDER_NO = "CT202601010001";
    private static final String AFTERSALE_NO = "AS202601010001";

    @Mock
    private CampusAftersaleMapper aftersaleMapper;
    @Mock
    private CampusOrderMapper orderMapper;
    @Mock
    private CampusOrderItemMapper orderItemMapper;
    @Mock
    private CampusProductMapper productMapper;
    @Mock
    private ICampusPaymentService paymentService;
    @Mock
    private ICampusNoticeService noticeService;

    @InjectMocks
    private CampusAftersaleServiceImpl aftersaleService;

    @Captor
    private ArgumentCaptor<CampusAftersale> aftersaleCaptor;
    @Captor
    private ArgumentCaptor<CampusOrder> orderCaptor;

    // ---------------- 申请 ----------------

    @Test
    @DisplayName("申请：订单不存在时拒绝")
    void applyRejectsMissingOrder() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.apply(BUYER_ID, "ry", ORDER_NO, 1, "商品与描述不符"));

        assertEquals("订单不存在", ex.getMessage());
        verify(aftersaleMapper, never()).insertCampusAftersale(any());
    }

    @Test
    @DisplayName("申请：他人订单按不存在处理")
    void applyRejectsOthersOrder() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(ORDER_NO, 999L, 2, "60.00"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.apply(BUYER_ID, "ry", ORDER_NO, 1, "商品与描述不符"));

        assertEquals("订单不存在", ex.getMessage());
    }

    @Test
    @DisplayName("申请：订单未发货/未完成时拒绝")
    void applyRejectsUnshippedOrder() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(ORDER_NO, BUYER_ID, 1, "60.00"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.apply(BUYER_ID, "ry", ORDER_NO, 1, "商品与描述不符"));

        assertEquals("订单未发货，暂不能申请售后", ex.getMessage());
    }

    @Test
    @DisplayName("申请：成功时落库售后单并把订单置为退款中(5)")
    void applySuccess() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(ORDER_NO, BUYER_ID, 2, "60.00"));

        String no = aftersaleService.apply(BUYER_ID, "ry", ORDER_NO, 2, "商品与描述不符");

        assertTrue(no.startsWith("AS"), "售后单号应以 AS 开头");
        verify(aftersaleMapper).insertCampusAftersale(aftersaleCaptor.capture());
        verify(orderMapper).updateCampusOrder(orderCaptor.capture());

        CampusAftersale saved = aftersaleCaptor.getValue();
        assertEquals(Long.valueOf(BUYER_ID), saved.getUserId());
        assertEquals(ORDER_NO, saved.getOrderNo());
        assertEquals(Integer.valueOf(2), saved.getType());
        assertEquals(Integer.valueOf(0), saved.getStatus());
        assertEquals(0, new BigDecimal("60.00").compareTo(saved.getRefundAmount()), "退款金额应取订单总额");
        assertEquals(Integer.valueOf(5), orderCaptor.getValue().getStatus());
    }

    // ---------------- 审核 ----------------

    @Test
    @DisplayName("审核：售后单不存在时拒绝")
    void auditRejectsMissingAftersale() {
        when(aftersaleMapper.selectByAftersaleNo(AFTERSALE_NO)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.audit(AFTERSALE_NO, "admin", true, null));

        assertEquals("售后单状态异常", ex.getMessage());
    }

    @Test
    @DisplayName("审核：已处理过的售后单拒绝重复审核")
    void auditRejectsAlreadyHandled() {
        when(aftersaleMapper.selectByAftersaleNo(AFTERSALE_NO)).thenReturn(aftersale(AFTERSALE_NO, ORDER_NO, 3));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.audit(AFTERSALE_NO, "admin", true, null));

        assertEquals("售后单状态异常", ex.getMessage());
        verify(paymentService, never()).refund(any(RefundCommand.class));
    }

    @Test
    @DisplayName("审核：关联订单缺失时显式失败（避免 NPE 与半成品状态）")
    void auditFailsFastWhenRelatedOrderMissing() {
        when(aftersaleMapper.selectByAftersaleNo(AFTERSALE_NO)).thenReturn(aftersale(AFTERSALE_NO, ORDER_NO, 0));
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.audit(AFTERSALE_NO, "admin", true, null));

        assertEquals("关联订单不存在，无法审核", ex.getMessage());
        // 关键：不得先退款/回库存再抛错，否则会留下已退款但订单状态未变的半成品
        verify(paymentService, never()).refund(any(RefundCommand.class));
        verify(productMapper, never()).increaseStock(anyLong(), anyInt());
        verify(aftersaleMapper, never()).updateCampusAftersale(any());
    }

    @Test
    @DisplayName("审核通过：退款 + 回补库存，售后单置已退款(3)、订单置已退款(6)")
    void auditPassRefundsAndReturnsStock() {
        CampusAftersale as = aftersale(AFTERSALE_NO, ORDER_NO, 0);
        when(aftersaleMapper.selectByAftersaleNo(AFTERSALE_NO)).thenReturn(as);
        CampusOrder order = order(ORDER_NO, BUYER_ID, 5, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectByOrderNo(ORDER_NO)).thenReturn(Collections.singletonList(item(PRODUCT_ID, 2)));
        when(paymentService.refund(any(RefundCommand.class))).thenReturn(RefundResult.success("REF-001"));

        assertTrue(aftersaleService.audit(AFTERSALE_NO, "admin", true, "同意退款"));

        ArgumentCaptor<RefundCommand> cmdCaptor = ArgumentCaptor.forClass(RefundCommand.class);
        verify(paymentService).refund(cmdCaptor.capture());
        RefundCommand sent = cmdCaptor.getValue();
        assertEquals(BUYER_ID, sent.getUserId());
        assertEquals(ORDER_NO, sent.getOrderNo());
        assertEquals(0, new BigDecimal("60.00").compareTo(sent.getAmount()));
        verify(productMapper).increaseStock(PRODUCT_ID, 2);
        assertEquals(Integer.valueOf(3), as.getStatus());
        assertEquals("admin", as.getHandler());
        assertNotNull(as.getHandleTime());
        assertEquals(Integer.valueOf(6), order.getStatus());
        verify(noticeService).push(eq(BUYER_ID), eq("aftersale"), anyString(), anyString(), eq(AFTERSALE_NO));
    }

    @Test
    @DisplayName("审核通过但退款失败：抛异常中止，不回库存不改状态（避免钱没退、库存已回）")
    void auditAbortsWhenRefundFails() {
        CampusAftersale as = aftersale(AFTERSALE_NO, ORDER_NO, 0);
        when(aftersaleMapper.selectByAftersaleNo(AFTERSALE_NO)).thenReturn(as);
        CampusOrder order = order(ORDER_NO, BUYER_ID, 5, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);
        // 注意：此处**不** stub orderItemMapper——退款失败发生在查订单明细之前，
        // 若 stub 了反而说明生产代码先做了回库存的准备（顺序错误）。
        when(paymentService.refund(any(RefundCommand.class))).thenReturn(RefundResult.failed("渠道超时"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> aftersaleService.audit(AFTERSALE_NO, "admin", true, "同意退款"));

        assertTrue(ex.getMessage().contains("退款失败"));
        verify(productMapper, never()).increaseStock(anyLong(), anyInt());
        verify(orderMapper, never()).updateCampusOrder(any());
        verify(aftersaleMapper, never()).updateCampusAftersale(any());
        assertEquals(Integer.valueOf(0), as.getStatus());
        assertEquals(Integer.valueOf(5), order.getStatus());
    }

    @Test
    @DisplayName("审核驳回：不退款不回库存，售后单置已驳回(2)、订单置已完成(3)")
    void auditRejectClosesOrder() {
        CampusAftersale as = aftersale(AFTERSALE_NO, ORDER_NO, 0);
        when(aftersaleMapper.selectByAftersaleNo(AFTERSALE_NO)).thenReturn(as);
        CampusOrder order = order(ORDER_NO, BUYER_ID, 5, "60.00");
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order);

        assertTrue(aftersaleService.audit(AFTERSALE_NO, "admin", false, "不符合退款条件"));

        verify(paymentService, never()).refund(any(RefundCommand.class));
        verify(productMapper, never()).increaseStock(anyLong(), anyInt());
        assertEquals(Integer.valueOf(2), as.getStatus());
        assertEquals(Integer.valueOf(3), order.getStatus());
        verify(noticeService).push(eq(BUYER_ID), eq("aftersale"), anyString(), anyString(), eq(AFTERSALE_NO));
    }

    @Test
    @DisplayName("概览：聚合统计直接委派 Mapper")
    void countGroupByStatusDelegates() {
        aftersaleService.countGroupByStatus(new CampusAftersale());

        verify(aftersaleMapper).countGroupByStatus(any());
    }

    // ---------------- 夹具 ----------------

    private CampusAftersale aftersale(String aftersaleNo, String orderNo, int status) {
        CampusAftersale as = new CampusAftersale();
        as.setAftersaleNo(aftersaleNo);
        as.setOrderNo(orderNo);
        as.setUserId(BUYER_ID);
        as.setRefundAmount(new BigDecimal("60.00"));
        as.setStatus(status);
        return as;
    }

    private CampusOrder order(String orderNo, Long userId, int status, String totalAmount) {
        CampusOrder o = new CampusOrder();
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal(totalAmount));
        return o;
    }

    private CampusOrderItem item(Long productId, int quantity) {
        CampusOrderItem item = new CampusOrderItem();
        item.setOrderNo(ORDER_NO);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
