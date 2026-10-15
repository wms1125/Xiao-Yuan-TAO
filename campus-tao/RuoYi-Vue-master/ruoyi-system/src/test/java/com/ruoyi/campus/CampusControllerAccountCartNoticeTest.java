package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.controller.CampusAccountController;
import com.ruoyi.campus.controller.CampusCartController;
import com.ruoyi.campus.controller.CampusNoticeController;
import com.ruoyi.campus.domain.CampusAccount;
import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.dto.CampusAccountRechargeDTO;
import com.ruoyi.campus.dto.CampusCartAddDTO;
import com.ruoyi.campus.service.ICampusAccountService;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * Controller 层测试：账户 / 购物车 / 站内通知。
 * 重点断言"用户身份一律取自登录态"，不接受前端传入。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 Controller：账户 / 购物车 / 站内通知")
class CampusControllerAccountCartNoticeTest {

    private static final long STUDENT_ID = 2L;
    private static final String STUDENT_NAME = "ry";

    @Mock
    private ICampusAccountService accountService;
    @Mock
    private ICampusCartService cartService;
    @Mock
    private ICampusNoticeService noticeService;

    @InjectMocks
    private CampusAccountController accountController;
    @InjectMocks
    private CampusCartController cartController;
    @InjectMocks
    private CampusNoticeController noticeController;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, STUDENT_NAME);
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    // ---------------- 账户 ----------------

    @Test
    @DisplayName("余额查询：以登录态 userId 查询本人账户")
    void balanceUsesLoginUser() {
        CampusAccount account = new CampusAccount();
        account.setBalance(new BigDecimal("440.00"));
        when(accountService.getOrCreate(STUDENT_ID, STUDENT_NAME)).thenReturn(account);

        AjaxResult result = accountController.balance();

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(account, result.get(AjaxResult.DATA_TAG));
        verify(accountService).getOrCreate(STUDENT_ID, STUDENT_NAME);
    }

    @Test
    @DisplayName("沙箱充值：金额透传，账户归属取自登录态")
    void rechargeUsesLoginUser() {
        CampusAccountRechargeDTO body = new CampusAccountRechargeDTO();
        body.setAmount(new BigDecimal("100.00"));

        AjaxResult result = accountController.recharge(body);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("充值成功", result.get(AjaxResult.MSG_TAG));
        verify(accountService).refund(STUDENT_ID, new BigDecimal("100.00"));
    }

    // ---------------- 购物车 ----------------

    @Test
    @DisplayName("购物车列表：仅返回本人数据")
    void cartListUsesLoginUser() {
        List<CampusCart> items = Collections.singletonList(new CampusCart());
        when(cartService.selectListByUserId(STUDENT_ID)).thenReturn(items);

        AjaxResult result = cartController.list();

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(items, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("加入购物车：userId/userName 取自登录态")
    void cartAddUsesLoginUser() {
        CampusCartAddDTO body = new CampusCartAddDTO();
        body.setProductId(12L);
        body.setQuantity(2);

        AjaxResult result = cartController.add(body);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        verify(cartService).addToCart(STUDENT_ID, STUDENT_NAME, 12L, 2);
    }

    @Test
    @DisplayName("修改数量：归属校验参数取自登录态")
    void cartUpdateQuantityUsesLoginUser() {
        CampusCart body = new CampusCart();
        body.setId(7L);
        body.setQuantity(3);

        cartController.updateQuantity(body);

        verify(cartService).updateQuantity(7L, 3, STUDENT_ID);
    }

    @Test
    @DisplayName("勾选状态：归属校验参数取自登录态")
    void cartToggleCheckedUsesLoginUser() {
        CampusCart body = new CampusCart();
        body.setId(7L);
        body.setChecked(1);

        cartController.toggleChecked(body);

        verify(cartService).toggleChecked(7L, 1, STUDENT_ID);
    }

    @Test
    @DisplayName("批量删除购物车项：归属校验参数取自登录态")
    void cartRemoveUsesLoginUser() {
        Long[] ids = new Long[] { 7L, 8L };

        cartController.remove(ids);

        verify(cartService).removeByIds(eq(ids), eq(STUDENT_ID));
    }

    // ---------------- 站内通知 ----------------

    @Test
    @DisplayName("通知列表：本人 + 未读过滤条件透传")
    void noticeListUsesLoginUser() {
        List<CampusNotice> list = Collections.singletonList(new CampusNotice());
        when(noticeService.selectListByUser(STUDENT_ID, 0, null)).thenReturn(list);

        AjaxResult result = noticeController.list(0);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(list, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("铃铛摘要：未读 Top5 + 未读总数")
    void noticeTopAggregatesUnread() {
        List<CampusNotice> unread = Arrays.asList(new CampusNotice(), new CampusNotice());
        when(noticeService.selectListByUser(STUDENT_ID, 0, 5)).thenReturn(unread);
        when(noticeService.countUnread(STUDENT_ID)).thenReturn(2);

        AjaxResult result = noticeController.top();

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) result.get(AjaxResult.DATA_TAG);
        assertSame(unread, data.get("items"));
        assertEquals(2, data.get("unreadCount"));
    }

    @Test
    @DisplayName("未读数量：按登录态统计")
    void noticeUnreadCountUsesLoginUser() {
        when(noticeService.countUnread(STUDENT_ID)).thenReturn(3);

        AjaxResult result = noticeController.unreadCount();

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals(3, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("标记已读：成功分支返回已读")
    void noticeMarkReadSuccess() {
        when(noticeService.markRead(9L, STUDENT_ID)).thenReturn(true);

        AjaxResult result = noticeController.read(9L);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("已读", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("标记已读：他人通知或不存在时返回错误")
    void noticeMarkReadDenied() {
        when(noticeService.markRead(9L, STUDENT_ID)).thenReturn(false);

        AjaxResult result = noticeController.read(9L);

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("通知不存在或无权操作", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("全部标记已读：返回影响行数")
    void noticeMarkReadAll() {
        when(noticeService.markReadAll(STUDENT_ID)).thenReturn(4);

        AjaxResult result = noticeController.readAll();

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals(4, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("未登录访问余额：SecurityUtils 抛 ServiceException（401）")
    void balanceWithoutLoginIsRejected() {
        CampusTestSupport.clear();

        com.ruoyi.common.exception.ServiceException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.ruoyi.common.exception.ServiceException.class, () -> accountController.balance());

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getCode());
        assertTrue(ex.getMessage().contains("获取用户ID异常"));
        verify(accountService, org.mockito.Mockito.never()).getOrCreate(any(), any());
    }
}
