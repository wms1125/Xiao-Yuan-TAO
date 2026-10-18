package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusFavorite;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.mapper.CampusFavoriteMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.ICampusFavoriteService;
import com.ruoyi.campus.service.impl.CampusFavoriteServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：商品收藏。
 *
 * <p>重点三条约束：
 * <ol>
 *   <li><b>重复收藏</b>：{@code uk_user_product} 唯一约束在 DB 层防重，服务层必须先判断再插入，
 *       否则 {@code DuplicateKeyException} 会以"系统异常"的形式抛给用户；</li>
 *   <li><b>越权取消</b>：{@code removeByFavoriteId} 的 SQL 自带 {@code user_id}，
 *       删 0 行时必须显式抛异常，而不是静默返回"成功"；</li>
 *   <li><b>商品必须存在</b>：收藏不存在的商品会留下孤儿记录（列表联表后标题为 null）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 商品收藏 Service")
class CampusFavoriteServiceTest {

    private static final long USER_ID = 2L;
    private static final long PRODUCT_ID = 100L;

    @Mock
    private CampusFavoriteMapper favoriteMapper;

    @Mock
    private CampusProductMapper productMapper;

    @InjectMocks
    private CampusFavoriteServiceImpl favoriteService;

    private CampusProduct onSaleProduct() {
        CampusProduct p = new CampusProduct();
        p.setId(PRODUCT_ID);
        p.setTitle("九成新 iPad");
        p.setPrice(new BigDecimal("1200.00"));
        p.setStatus(1);
        return p;
    }

    // ---------------- toggle ----------------

    @Test
    @DisplayName("收藏：未收藏时插入并返回 true")
    void toggleAddsWhenNotFavored() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(favoriteMapper.countByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(0);
        when(favoriteMapper.countByUserId(USER_ID)).thenReturn(3);
        when(favoriteMapper.insertIfUnderLimit(USER_ID, PRODUCT_ID,
                ICampusFavoriteService.MAX_FAVORITES)).thenReturn(1);

        assertTrue(favoriteService.toggle(USER_ID, PRODUCT_ID));

        // 改为带上限守卫的插入：并发下也不会超过 MAX_FAVORITES
        verify(favoriteMapper).insertIfUnderLimit(USER_ID, PRODUCT_ID, ICampusFavoriteService.MAX_FAVORITES);
        verify(favoriteMapper, never()).deleteByUserAndProduct(anyLong(), anyLong());
    }

    // ---------------- 收藏数量上限 ----------------

    @Test
    @DisplayName("上限边界：正好 MAX-1 个时仍可收藏")
    void allowsFavoriteAtMaxMinusOne() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(favoriteMapper.countByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(0);
        when(favoriteMapper.countByUserId(USER_ID)).thenReturn(ICampusFavoriteService.MAX_FAVORITES - 1);
        when(favoriteMapper.insertIfUnderLimit(USER_ID, PRODUCT_ID,
                ICampusFavoriteService.MAX_FAVORITES)).thenReturn(1);

        assertTrue(favoriteService.toggle(USER_ID, PRODUCT_ID));
    }

    @Test
    @DisplayName("上限边界：达到 MAX 个时拒绝收藏，且给出明确提示")
    void rejectsFavoriteAtMax() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(favoriteMapper.countByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(0);
        when(favoriteMapper.countByUserId(USER_ID)).thenReturn(ICampusFavoriteService.MAX_FAVORITES);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.toggle(USER_ID, PRODUCT_ID));

        // 提示必须包含上限值和下一步动作——只说"收藏失败"用户不知道该干什么
        assertTrue(ex.getMessage().contains("最多 " + ICampusFavoriteService.MAX_FAVORITES + " 个"), ex.getMessage());
        // 已满时不该再去尝试插入
        verify(favoriteMapper, never()).insertIfUnderLimit(anyLong(), anyLong(), eq(ICampusFavoriteService.MAX_FAVORITES));
    }

    @Test
    @DisplayName("负向：并发竞态下插入返回 0 行，同样报上限错误（不误导用户）")
    void rejectsWhenInsertGuardBlocks() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(favoriteMapper.countByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(0);
        // 检查时还没满，但两个并发请求同时通过了检查
        when(favoriteMapper.countByUserId(USER_ID)).thenReturn(ICampusFavoriteService.MAX_FAVORITES - 1);
        // SQL 层的守卫拦住后一个：插入 0 行
        when(favoriteMapper.insertIfUnderLimit(USER_ID, PRODUCT_ID,
                ICampusFavoriteService.MAX_FAVORITES)).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.toggle(USER_ID, PRODUCT_ID));

        assertTrue(ex.getMessage().contains("已达上限"), ex.getMessage());
    }

    @Test
    @DisplayName("已达上限时取消收藏仍然可用（上限只挡新增，不挡删除）")
    void removeStillWorksAtMax() {
        when(favoriteMapper.deleteByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(1);

        assertTrue(favoriteService.remove(USER_ID, PRODUCT_ID));
    }

    @Test
    @DisplayName("取消收藏：已收藏时删除并返回 false")
    void toggleRemovesWhenAlreadyFavored() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(favoriteMapper.countByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(1);

        assertFalse(favoriteService.toggle(USER_ID, PRODUCT_ID));

        verify(favoriteMapper).deleteByUserAndProduct(USER_ID, PRODUCT_ID);
        // 关键：已收藏时不得再插入，否则撞 uk_user_product 抛 DuplicateKeyException
        verify(favoriteMapper, never()).insertCampusFavorite(any());
    }

    @Test
    @DisplayName("负向：收藏不存在的商品被拒绝，不产生孤儿记录")
    void toggleRejectsMissingProduct() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.toggle(USER_ID, PRODUCT_ID));

        assertEquals("商品不存在", ex.getMessage());
        verify(favoriteMapper, never()).insertCampusFavorite(any());
    }

    @Test
    @DisplayName("负向：未登录（userId 为空）被拒绝")
    void toggleRejectsNullUser() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.toggle(null, PRODUCT_ID));

        assertTrue(ex.getMessage().contains("未登录"));
        verify(productMapper, never()).selectCampusProductById(anyLong());
    }

    // ---------------- remove ----------------

    @Test
    @DisplayName("取消收藏：未收藏过返回 false 而不抛异常")
    void removeReturnsFalseWhenNotFavored() {
        when(favoriteMapper.deleteByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(0);

        assertFalse(favoriteService.remove(USER_ID, PRODUCT_ID));
    }

    @Test
    @DisplayName("负向：按收藏 ID 越权取消时抛异常，而不是静默返回成功")
    void removeByFavoriteIdRejectsUnauthorized() {
        when(favoriteMapper.deleteByFavoriteId(999L, USER_ID)).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.removeByFavoriteId(999L, USER_ID));

        assertEquals("收藏记录不存在或无权操作", ex.getMessage());
    }

    @Test
    @DisplayName("按收藏 ID 取消：SQL 必须带上 userId（防越权删除）")
    void removeByFavoriteIdPassesUserId() {
        when(favoriteMapper.deleteByFavoriteId(100L, USER_ID)).thenReturn(1);

        favoriteService.removeByFavoriteId(100L, USER_ID);

        // 若 SQL 漏了 user_id，这条用例会因参数不匹配而失败
        verify(favoriteMapper).deleteByFavoriteId(100L, USER_ID);
    }

    // ---------------- 批量状态查询 ----------------

    @Test
    @DisplayName("批量收藏状态：未登录或空商品列表返回空集合，不发查询")
    void selectFavoredSkipsEmpty() {
        assertEquals(Collections.emptyList(), favoriteService.selectFavoredProductIds(null, Arrays.asList(1L, 2L)));
        assertEquals(Collections.emptyList(), favoriteService.selectFavoredProductIds(USER_ID, Collections.emptyList()));
        verify(favoriteMapper, never()).selectFavoredProductIds(anyLong(), any());
    }

    @Test
    @DisplayName("批量收藏状态：返回已收藏的商品 ID 集合")
    void selectFavoredReturnsIds() {
        List<Long> ids = Arrays.asList(100L, 102L);
        when(favoriteMapper.selectFavoredProductIds(USER_ID, Arrays.asList(100L, 101L, 102L)))
                .thenReturn(ids);

        assertEquals(ids, favoriteService.selectFavoredProductIds(USER_ID, Arrays.asList(100L, 101L, 102L)));
    }

    // ---------------- 列表与计数 ----------------

    @Test
    @DisplayName("收藏列表：联表冗余字段随查询返回，可直接用于前端渲染")
    void selectListReturnsProductSummary() {
        CampusFavorite f = new CampusFavorite();
        f.setFavoriteId(1L);
        f.setProductId(PRODUCT_ID);
        f.setProductTitle("九成新 iPad");
        f.setProductPrice(new BigDecimal("1200.00"));
        f.setProductStatus(1);
        f.setSellerName("李雨桐");
        when(favoriteMapper.selectListByUser(USER_ID)).thenReturn(Collections.singletonList(f));

        List<CampusFavorite> list = favoriteService.selectListByUser(USER_ID);

        assertEquals(1, list.size());
        assertEquals("九成新 iPad", list.get(0).getProductTitle());
        assertEquals("李雨桐", list.get(0).getSellerName());
    }

    @Test
    @DisplayName("收藏计数：直接委派 Mapper")
    void countByUserDelegates() {
        when(favoriteMapper.countByUserId(USER_ID)).thenReturn(3);

        assertEquals(3, favoriteService.countByUser(USER_ID));
    }

    // ---------------- 插入参数保真 ----------------

    @Test
    @DisplayName("插入收藏：userId 必须来自登录态而非入参（防止替他人收藏）")
    void insertUsesLoginUserId() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(favoriteMapper.countByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(0);
        when(favoriteMapper.countByUserId(USER_ID)).thenReturn(1);
        when(favoriteMapper.insertIfUnderLimit(USER_ID, PRODUCT_ID,
                ICampusFavoriteService.MAX_FAVORITES)).thenReturn(1);

        favoriteService.toggle(USER_ID, PRODUCT_ID);

        // 守卫式插入直接以登录态 userId 与 productId 为参数，没有中间对象可被改写
        verify(favoriteMapper).insertIfUnderLimit(USER_ID, PRODUCT_ID, ICampusFavoriteService.MAX_FAVORITES);
    }

    @Test
    @DisplayName("负向：按收藏 ID 取消时 userId 为空被拒绝")
    void removeByFavoriteIdRejectsNullUser() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.removeByFavoriteId(100L, null));

        assertTrue(ex.getMessage().contains("未登录"));
        verify(favoriteMapper, never()).deleteByFavoriteId(anyLong(), eq(null));
    }
}
