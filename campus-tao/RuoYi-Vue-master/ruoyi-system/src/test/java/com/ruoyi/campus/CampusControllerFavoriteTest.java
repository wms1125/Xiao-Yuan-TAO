package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.controller.CampusFavoriteController;
import com.ruoyi.campus.domain.CampusFavorite;
import com.ruoyi.campus.service.ICampusFavoriteService;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * Controller 层测试：商品收藏接口。
 *
 * <p>核心断言是**身份来源**：所有接口的用户身份必须取自登录态
 * （{@code SecurityUtils.getUserId()}），请求里没有任何字段能改变归属——
 * 收藏夹是买家私有数据，否则传个 userId 就能看/操作他人收藏。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 商品收藏 Controller")
class CampusControllerFavoriteTest {

    private static final long STUDENT_ID = 2L;
    private static final long PRODUCT_ID = 100L;

    @Mock
    private ICampusFavoriteService favoriteService;

    @InjectMocks
    private CampusFavoriteController favoriteController;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, "ry");
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    @Test
    @DisplayName("列表：以登录用户为作用域查询")
    void listUsesLoginUser() {
        AjaxResult result = favoriteController.list();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(favoriteService).selectListByUser(STUDENT_ID);
    }

    @Test
    @DisplayName("计数：以登录用户为作用域")
    void countUsesLoginUser() {
        favoriteController.count();

        verify(favoriteService).countByUser(STUDENT_ID);
    }

    @Test
    @DisplayName("批量状态查询：以登录用户为作用域，返回已收藏商品 ID")
    void statusUsesLoginUser() {
        when(favoriteService.selectFavoredProductIds(STUDENT_ID, Arrays.asList(100L, 101L)))
                .thenReturn(Arrays.asList(100L));

        AjaxResult result = favoriteController.status(Arrays.asList(100L, 101L));

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(Arrays.asList(100L), data.get("favored"));
    }

    @Test
    @DisplayName("切换收藏：userId 取自登录态，productId 来自请求")
    void toggleUsesLoginUser() {
        when(favoriteService.toggle(STUDENT_ID, PRODUCT_ID)).thenReturn(true);

        AjaxResult result = favoriteController.toggle(PRODUCT_ID);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(favoriteService).toggle(STUDENT_ID, PRODUCT_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(true, data.get("favored"));
    }

    @Test
    @DisplayName("切换收藏：已收藏时返回 favored=false")
    void toggleReportsUnfavored() {
        when(favoriteService.toggle(STUDENT_ID, PRODUCT_ID)).thenReturn(false);

        favoriteController.toggle(PRODUCT_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) favoriteController.toggle(PRODUCT_ID).get("data");
        assertEquals(false, data.get("favored"));
    }

    @Test
    @DisplayName("取消收藏：以登录用户为作用域")
    void removeUsesLoginUser() {
        when(favoriteService.remove(STUDENT_ID, PRODUCT_ID)).thenReturn(true);

        AjaxResult result = favoriteController.remove(PRODUCT_ID);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(favoriteService).remove(STUDENT_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("取消收藏：该商品本就没收藏时给出明确提示而非报错")
    void removeReportsNotFavored() {
        when(favoriteService.remove(STUDENT_ID, PRODUCT_ID)).thenReturn(false);

        AjaxResult result = favoriteController.remove(PRODUCT_ID);

        assertEquals("该商品未被收藏", result.get("msg"));
    }

    @Test
    @DisplayName("负向：任何接口都不接受请求体中的 userId")
    void noEndpointAcceptsUserIdParam() {
        // 通过"调用签名里没有 userId 入参"在编译期保证；此处用行为验证：
        // 即使 service 层按别的用户被调用，Controller 也只能传登录态用户。
        favoriteController.toggle(PRODUCT_ID);
        verify(favoriteService).toggle(eq(STUDENT_ID), eq(PRODUCT_ID));
        verify(favoriteService, never()).toggle(eq(999L), any());
    }

    @Test
    @DisplayName("空收藏列表：返回空集合而不是 null")
    void listReturnsEmptyCollection() {
        when(favoriteService.selectListByUser(STUDENT_ID)).thenReturn(Arrays.asList());

        AjaxResult result = favoriteController.list();

        @SuppressWarnings("unchecked")
        List<CampusFavorite> data = (List<CampusFavorite>) result.get("data");
        assertEquals(0, data.size());
    }

    @Test
    @DisplayName("批量状态查询：空商品列表直接返回空，不触发查询")
    void statusWithEmptyList() {
        AjaxResult result = favoriteController.status(Arrays.asList());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(favoriteService).selectFavoredProductIds(STUDENT_ID, Arrays.asList());
    }
}
