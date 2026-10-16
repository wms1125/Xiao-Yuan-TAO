package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.service.impl.CampusCartServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：购物车。
 *
 * <p>此前该类分支覆盖为 0。重点四类约束：
 * <ol>
 *   <li><b>数量归一</b>：quantity 为 null / 0 / 负数一律按 1 处理——
 *       前端传脏值不能让购物车出现 0 件或负数件；</li>
 *   <li><b>重复加购合并</b>：同一用户同一商品已有记录时数量累加（update），不得插入第二条，
 *       否则结算时同一商品出现两行、总份数翻倍；</li>
 *   <li><b>越权防护</b>：改数量 / 勾选 / 删除都必须校验记录归属 userId，
 *       他人记录一律 ServiceException（删除是逐条过滤而非整批拒绝）；</li>
 *   <li><b>结算清理</b>：cleanChecked 只清勾选（checked=1）的记录，未勾选的保留。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 购物车 Service")
class CampusCartServiceTest {

    private static final long USER_ID = 2L;
    private static final long OTHER_ID = 999L;
    private static final long CART_ID = 10L;

    @Mock
    private CampusCartMapper cartMapper;

    @InjectMocks
    private CampusCartServiceImpl cartService;

    private CampusCart ownedCart() {
        CampusCart cart = new CampusCart();
        cart.setId(CART_ID);
        cart.setUserId(USER_ID);
        cart.setProductId(100L);
        cart.setQuantity(2);
        cart.setChecked(1);
        return cart;
    }

    @Test
    @DisplayName("加购数量为 null / 0 / 负数时一律按 1 处理")
    void addDefaultsQuantityWhenNullOrNonPositive() {
        when(cartMapper.selectByUserAndProduct(USER_ID, 100L)).thenReturn(null);

        cartService.addToCart(USER_ID, "张三", 100L, null);
        cartService.addToCart(USER_ID, "张三", 100L, 0);
        cartService.addToCart(USER_ID, "张三", 100L, -2);

        ArgumentCaptor<CampusCart> captor = ArgumentCaptor.forClass(CampusCart.class);
        verify(cartMapper, org.mockito.Mockito.times(3)).insertCampusCart(captor.capture());
        for (CampusCart sent : captor.getAllValues()) {
            assertEquals(Integer.valueOf(1), sent.getQuantity(), "脏数量必须归一为 1");
            assertEquals(Integer.valueOf(1), sent.getChecked(), "新加购默认勾选");
            assertEquals(USER_ID, sent.getUserId());
        }
    }

    @Test
    @DisplayName("无既有记录时新插入，数量与勾选按传入/默认值")
    void addNewItemInsertsChecked() {
        when(cartMapper.selectByUserAndProduct(USER_ID, 100L)).thenReturn(null);

        cartService.addToCart(USER_ID, "张三", 100L, 3);

        ArgumentCaptor<CampusCart> captor = ArgumentCaptor.forClass(CampusCart.class);
        verify(cartMapper).insertCampusCart(captor.capture());
        CampusCart sent = captor.getValue();
        assertEquals(Integer.valueOf(3), sent.getQuantity());
        assertEquals(Integer.valueOf(1), sent.getChecked());
        assertEquals("张三", sent.getUserName());
        verify(cartMapper, never()).updateCampusCart(any(CampusCart.class));
    }

    @Test
    @DisplayName("重复加购合并数量（update），不再插入第二条")
    void addExistingMergesQuantity() {
        CampusCart exist = ownedCart();
        when(cartMapper.selectByUserAndProduct(USER_ID, 100L)).thenReturn(exist);

        cartService.addToCart(USER_ID, "张三", 100L, 3);

        ArgumentCaptor<CampusCart> captor = ArgumentCaptor.forClass(CampusCart.class);
        verify(cartMapper).updateCampusCart(captor.capture());
        assertEquals(Integer.valueOf(5), captor.getValue().getQuantity(), "2 + 3 = 5");
        verify(cartMapper, never()).insertCampusCart(any(CampusCart.class));
    }

    @Test
    @DisplayName("归属人可以改数量")
    void updateQuantityOwnedSucceeds() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(ownedCart());

        cartService.updateQuantity(CART_ID, 4, USER_ID);

        ArgumentCaptor<CampusCart> captor = ArgumentCaptor.forClass(CampusCart.class);
        verify(cartMapper).updateCampusCart(captor.capture());
        assertEquals(Integer.valueOf(4), captor.getValue().getQuantity());
    }

    @Test
    @DisplayName("负向：改他人购物车数量被拒")
    void updateQuantityNotOwnedRejected() {
        CampusCart others = ownedCart();
        others.setUserId(OTHER_ID);
        when(cartMapper.selectCartById(CART_ID)).thenReturn(others);

        assertThrows(ServiceException.class, () -> cartService.updateQuantity(CART_ID, 4, USER_ID));
        verify(cartMapper, never()).updateCampusCart(any(CampusCart.class));
    }

    @Test
    @DisplayName("负向：改不存在的购物车记录被拒")
    void updateQuantityMissingRejected() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> cartService.updateQuantity(CART_ID, 4, USER_ID));
    }

    @Test
    @DisplayName("归属人可以切换勾选状态")
    void toggleCheckedOwnedSucceeds() {
        when(cartMapper.selectCartById(CART_ID)).thenReturn(ownedCart());

        cartService.toggleChecked(CART_ID, 0, USER_ID);

        ArgumentCaptor<CampusCart> captor = ArgumentCaptor.forClass(CampusCart.class);
        verify(cartMapper).updateCampusCart(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getChecked());
    }

    @Test
    @DisplayName("负向：切换他人购物车勾选被拒")
    void toggleCheckedNotOwnedRejected() {
        CampusCart others = ownedCart();
        others.setUserId(OTHER_ID);
        when(cartMapper.selectCartById(CART_ID)).thenReturn(others);

        assertThrows(ServiceException.class, () -> cartService.toggleChecked(CART_ID, 0, USER_ID));
        verify(cartMapper, never()).updateCampusCart(any(CampusCart.class));
    }

    @Test
    @DisplayName("批量删除：ids 为 null 时零删除")
    void removeByIdsIgnoresNullArray() {
        int n = cartService.removeByIds(null, USER_ID);

        assertEquals(0, n);
        verify(cartMapper, never()).deleteCampusCartById(any());
    }

    @Test
    @DisplayName("批量删除：只删本人记录，他人记录/不存在/null 元素跳过")
    void removeByIdsOnlyDeletesOwn() {
        CampusCart othersCart = new CampusCart();
        othersCart.setId(20L);
        othersCart.setUserId(OTHER_ID);
        when(cartMapper.selectCartById(10L)).thenReturn(ownedCart());
        when(cartMapper.selectCartById(20L)).thenReturn(othersCart);
        // 30L 不 stub——mockito 缺省返回 null，正好对应"记录已不存在"
        // mockito 对未 stub 的 int 返回缺省 0，须显式 stub 才能断言"计入了删除数"
        when(cartMapper.deleteCampusCartById(10L)).thenReturn(1);

        int n = cartService.removeByIds(new Long[] { 10L, 20L, 30L, null }, USER_ID);

        assertEquals(1, n, "只有 10L 是本人的记录");
        verify(cartMapper).deleteCampusCartById(10L);
        verify(cartMapper, never()).deleteCampusCartById(20L);
        verify(cartMapper, never()).deleteCampusCartById(30L);
    }

    @Test
    @DisplayName("按用户查询：透传 userId 条件")
    void selectListByUserIdPassesFilter() {
        List<CampusCart> expected = Collections.singletonList(ownedCart());
        when(cartMapper.selectCartList(any(CampusCart.class))).thenReturn(expected);

        List<CampusCart> result = cartService.selectListByUserId(USER_ID);

        assertEquals(expected, result);
        ArgumentCaptor<CampusCart> captor = ArgumentCaptor.forClass(CampusCart.class);
        verify(cartMapper).selectCartList(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("结算清理：只删勾选记录并返回删除数")
    void cleanCheckedDeletesOnlyChecked() {
        CampusCart a = ownedCart();
        a.setId(10L);
        CampusCart b = ownedCart();
        b.setId(11L);
        when(cartMapper.selectCartList(any(CampusCart.class))).thenReturn(Arrays.asList(a, b));
        // mockito 对未 stub 的 int 返回缺省 0，须显式 stub 才能断言"计入了删除数"
        when(cartMapper.deleteCampusCartById(10L)).thenReturn(1);
        when(cartMapper.deleteCampusCartById(11L)).thenReturn(1);

        int n = cartService.cleanChecked(USER_ID);

        assertEquals(2, n);
        verify(cartMapper).deleteCampusCartById(10L);
        verify(cartMapper).deleteCampusCartById(11L);
    }

    @Test
    @DisplayName("结算清理：无勾选记录时返回 0 且不触发删除")
    void cleanCheckedEmptyDeletesNothing() {
        when(cartMapper.selectCartList(any(CampusCart.class))).thenReturn(Collections.emptyList());

        int n = cartService.cleanChecked(USER_ID);

        assertEquals(0, n);
        verify(cartMapper, never()).deleteCampusCartById(any());
    }
}
