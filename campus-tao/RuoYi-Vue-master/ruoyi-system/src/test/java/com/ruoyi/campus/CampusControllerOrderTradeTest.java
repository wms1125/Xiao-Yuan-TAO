package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.controller.CampusAftersaleController;
import com.ruoyi.campus.controller.CampusLogisticsController;
import com.ruoyi.campus.controller.CampusOrderController;
import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.CampusLogisticsTrace;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.dto.CampusAftersaleApplyDTO;
import com.ruoyi.campus.dto.CampusAftersaleAuditDTO;
import com.ruoyi.campus.dto.CampusOrderCreateDTO;
import com.ruoyi.campus.service.ICampusAftersaleService;
import com.ruoyi.campus.service.ICampusLogisticsService;
import com.ruoyi.campus.service.ICampusOrderService;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * Controller 层测试：订单 / 售后 / 物流轨迹——重点覆盖归属与越权判定分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 Controller：订单 / 售后 / 物流轨迹")
class CampusControllerOrderTradeTest {

    private static final long ADMIN_ID = 1L;
    private static final long STUDENT_ID = 2L;
    private static final String STUDENT_NAME = "ry";
    private static final String ORDER_NO = "CT202601010001";

    @Mock
    private ICampusOrderService orderService;
    @Mock
    private ICampusAftersaleService aftersaleService;
    @Mock
    private ICampusLogisticsService logisticsService;

    @InjectMocks
    private CampusOrderController orderController;
    @InjectMocks
    private CampusAftersaleController aftersaleController;
    @InjectMocks
    private CampusLogisticsController logisticsController;

    @Captor
    private ArgumentCaptor<CampusOrder> orderCaptor;
    @Captor
    private ArgumentCaptor<CampusAftersale> aftersaleCaptor;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, STUDENT_NAME);
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    // ---------------- 订单 ----------------

    @Test
    @DisplayName("下单：买家身份取自登录态，购物车项转 Long[]")
    void createOrderUsesLoginUser() {
        CampusOrderCreateDTO body = new CampusOrderCreateDTO();
        body.setCartIds(Arrays.asList(3L, 4L));
        body.setReceiverName("张三");
        body.setReceiverPhone("13800000000");
        body.setAddress("1号楼101");
        body.setRemark("尽快发货");
        when(orderService.createOrder(any(), any(), any(), any(), any(), any(), any())).thenReturn(ORDER_NO);

        AjaxResult result = orderController.create(body);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("下单成功", result.get(AjaxResult.MSG_TAG));
        assertEquals(ORDER_NO, result.get(AjaxResult.DATA_TAG));
        verify(orderService).createOrder(eq(STUDENT_ID), eq(STUDENT_NAME), aryEq(new Long[] { 3L, 4L }),
                eq("张三"), eq("13800000000"), eq("1号楼101"), eq("尽快发货"));
    }

    @Test
    @DisplayName("支付成功：返回支付成功")
    void payOrderSuccess() {
        when(orderService.payOrder(ORDER_NO, STUDENT_ID)).thenReturn(true);

        AjaxResult result = orderController.pay(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("支付成功", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("余额不足：返回充值提示")
    void payOrderInsufficientBalance() {
        when(orderService.payOrder(ORDER_NO, STUDENT_ID)).thenReturn(false);

        AjaxResult result = orderController.pay(ORDER_NO);

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("沙箱余额不足，请先充值", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("取消订单：归属校验参数取自登录态")
    void cancelOrderUsesLoginUser() {
        AjaxResult result = orderController.cancel(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        verify(orderService).cancelOrder(ORDER_NO, STUDENT_ID);
    }

    @Test
    @DisplayName("发货：物流单号透传")
    void shipOrderPassesLogisticsNo() {
        AjaxResult result = orderController.ship(ORDER_NO, "SF123456789");

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        verify(orderService).shipOrder(ORDER_NO, "SF123456789");
    }

    @Test
    @DisplayName("确认收货：归属校验参数取自登录态")
    void finishOrderUsesLoginUser() {
        AjaxResult result = orderController.finish(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        verify(orderService).finishOrder(ORDER_NO, STUDENT_ID);
    }

    @Test
    @DisplayName("我的订单：普通用户查询条件被强制改写为本人")
    void orderListForcesCurrentUser() {
        CampusOrder query = new CampusOrder();
        query.setUserId(99L);
        when(orderService.selectList(any())).thenReturn(Collections.emptyList());

        orderController.list(query);

        verify(orderService).selectList(orderCaptor.capture());
        assertEquals(STUDENT_ID, orderCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("我的订单：管理员不加归属过滤")
    void orderListForAdminKeepsFilterOpen() {
        CampusTestSupport.loginAs(ADMIN_ID, "admin");
        CampusOrder query = new CampusOrder();
        when(orderService.selectList(any())).thenReturn(Collections.emptyList());

        orderController.list(query);

        verify(orderService).selectList(orderCaptor.capture());
        assertNull(orderCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("订单详情：本人可查看")
    void detailOwnOrderAllowed() {
        CampusOrder order = new CampusOrder();
        order.setOrderNo(ORDER_NO);
        order.setUserId(STUDENT_ID);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);

        AjaxResult result = orderController.detail(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(order, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("订单详情：他人订单被拒（数据不存在时同样被拒）")
    void detailOthersOrderDenied() {
        CampusOrder order = new CampusOrder();
        order.setOrderNo(ORDER_NO);
        order.setUserId(7L);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);

        AjaxResult result = orderController.detail(ORDER_NO);

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("无权查看该订单", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("订单详情：订单不存在时统一按无权限返回，不泄露存在性")
    void detailMissingOrderDenied() {
        when(orderService.selectWithItems("CT404")).thenReturn(null);

        AjaxResult result = orderController.detail("CT404");

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("无权查看该订单", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("订单详情：管理员可查看他人订单")
    void detailForAdminAllowed() {
        CampusTestSupport.loginAs(ADMIN_ID, "admin");
        CampusOrder order = new CampusOrder();
        order.setOrderNo(ORDER_NO);
        order.setUserId(7L);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);

        AjaxResult result = orderController.detail(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
    }

    // ---------------- 售后 ----------------

    @Test
    @DisplayName("售后申请：申请人取自登录态")
    void aftersaleApplyUsesLoginUser() {
        CampusAftersaleApplyDTO body = new CampusAftersaleApplyDTO();
        body.setOrderNo(ORDER_NO);
        body.setType(1);
        body.setReason("商品与描述不符");
        when(aftersaleService.apply(any(), any(), any(), any(), any())).thenReturn("AS202601010001");

        AjaxResult result = aftersaleController.apply(body);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("申请已提交", result.get(AjaxResult.MSG_TAG));
        assertEquals("AS202601010001", result.get(AjaxResult.DATA_TAG));
        verify(aftersaleService).apply(STUDENT_ID, STUDENT_NAME, ORDER_NO, 1, "商品与描述不符");
    }

    @Test
    @DisplayName("售后审核：审核人强制取自登录态，忽略前端传入")
    void aftersaleAuditForcesHandlerFromLogin() {
        CampusTestSupport.loginAs(ADMIN_ID, "admin");
        CampusAftersaleAuditDTO body = new CampusAftersaleAuditDTO();
        body.setAftersaleNo("AS202601010001");
        body.setPass(true);
        body.setRemark("同意退款");

        AjaxResult result = aftersaleController.audit(body);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("审核完成", result.get(AjaxResult.MSG_TAG));
        verify(aftersaleService).audit("AS202601010001", "admin", true, "同意退款");
    }

    @Test
    @DisplayName("我的售后：普通用户被强制改写为本人")
    void aftersaleListForcesCurrentUser() {
        CampusAftersale query = new CampusAftersale();
        query.setUserId(99L);
        when(aftersaleService.selectList(any())).thenReturn(Collections.emptyList());

        aftersaleController.list(query);

        verify(aftersaleService).selectList(aftersaleCaptor.capture());
        assertEquals(STUDENT_ID, aftersaleCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("我的售后：管理员不加归属过滤")
    void aftersaleListForAdminKeepsFilterOpen() {
        CampusTestSupport.loginAs(ADMIN_ID, "admin");
        CampusAftersale query = new CampusAftersale();
        when(aftersaleService.selectList(any())).thenReturn(Collections.emptyList());

        aftersaleController.list(query);

        verify(aftersaleService).selectList(aftersaleCaptor.capture());
        assertNull(aftersaleCaptor.getValue().getUserId());
    }

    // ---------------- 物流轨迹 ----------------

    @Test
    @DisplayName("通道状态：未配置网关时为 false（前端据此隐藏轨迹区块）")
    void logisticsStatusReflectsProvider() {
        when(logisticsService.providerEnabled()).thenReturn(false);

        AjaxResult result = logisticsController.status();

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals(false, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("轨迹查询：本人订单可查，返回物流单号与轨迹节点")
    void traceOwnOrderAllowed() {
        CampusOrder order = new CampusOrder();
        order.setUserId(STUDENT_ID);
        order.setLogisticsNo("SF123456789");
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);
        when(logisticsService.providerEnabled()).thenReturn(true);
        when(logisticsService.listTrace(ORDER_NO))
                .thenReturn(Collections.singletonList(new CampusLogisticsTrace()));

        AjaxResult result = logisticsController.trace(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) result.get(AjaxResult.DATA_TAG);
        assertEquals(ORDER_NO, data.get("orderNo"));
        assertEquals("SF123456789", data.get("logisticsNo"));
        assertEquals(true, data.get("providerEnabled"));
        assertEquals(1, ((java.util.List<?>) data.get("traces")).size());
    }

    @Test
    @DisplayName("轨迹查询：他人订单被拒")
    void traceOthersOrderDenied() {
        CampusOrder order = new CampusOrder();
        order.setUserId(7L);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);

        AjaxResult result = logisticsController.trace(ORDER_NO);

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("无权查看该订单物流轨迹", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("轨迹查询：订单不存在时被拒")
    void traceMissingOrderDenied() {
        when(orderService.selectWithItems("CT404")).thenReturn(null);

        AjaxResult result = logisticsController.trace("CT404");

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
    }

    @Test
    @DisplayName("轨迹查询：管理员可查他人订单")
    void traceForAdminAllowed() {
        CampusTestSupport.loginAs(ADMIN_ID, "admin");
        CampusOrder order = new CampusOrder();
        order.setUserId(7L);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);
        when(logisticsService.providerEnabled()).thenReturn(false);
        when(logisticsService.listTrace(ORDER_NO)).thenReturn(Collections.emptyList());

        AjaxResult result = logisticsController.trace(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
    }

    @Test
    @DisplayName("手工同步：通道未配置时明确降级提示，不抛异常")
    void syncDegradesWhenProviderDisabled() {
        CampusOrder order = new CampusOrder();
        order.setUserId(STUDENT_ID);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);
        when(logisticsService.providerEnabled()).thenReturn(false);

        AjaxResult result = logisticsController.sync(ORDER_NO);

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("物流轨迹查询通道未配置，已降级为仅记录物流单号", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("手工同步：他人订单被拒")
    void syncOthersOrderDenied() {
        CampusOrder order = new CampusOrder();
        order.setUserId(7L);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);

        AjaxResult result = logisticsController.sync(ORDER_NO);

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("无权操作该订单物流轨迹", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("手工同步：通道可用时返回新增节点数")
    void syncReturnsAddedCount() {
        CampusOrder order = new CampusOrder();
        order.setUserId(STUDENT_ID);
        when(orderService.selectWithItems(ORDER_NO)).thenReturn(order);
        when(logisticsService.providerEnabled()).thenReturn(true);
        when(logisticsService.syncTrace(ORDER_NO)).thenReturn(3);

        AjaxResult result = logisticsController.sync(ORDER_NO);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("已同步物流轨迹，新增 3 条节点", result.get(AjaxResult.MSG_TAG));
    }
}
