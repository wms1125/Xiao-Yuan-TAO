package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.impl.CampusProductServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：商品管理。
 *
 * <p>覆盖四类约束（此前该类分支覆盖为 0，是覆盖率最大的单点缺口）：
 * <ol>
 *   <li><b>卖家身份服务端强制</b>：insert 覆盖前端传入的 sellerId/sellerName，
 *       防止伪造他人名义发布；status 缺省上架（1），显式传入则尊重；</li>
 *   <li><b>越权写防护</b>：update / changeStatus 仅商品归属人或管理员（userId=1）可操作，
 *       非归属人即使拿到 id 也必须被 ServiceException 拦下，且不得触达 mapper 写操作；</li>
 *   <li><b>批量删除按归属过滤</b>：逐条校验归属，本人商品删、他人商品与已删商品跳过，
 *       ids 为 null 或调用方 userId 为 null 时零删除；</li>
 *   <li><b>状态只改 status</b>：changeStatus 构造的更新对象只带 id + status，
 *       不得把其他字段顺带覆盖（防止把标题/价格重置为 null）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 商品管理 Service")
class CampusProductServiceTest {

    private static final long SELLER_ID = 2L;
    private static final long OTHER_ID = 999L;
    private static final long ADMIN_ID = 1L;
    private static final long PRODUCT_ID = 100L;

    @Mock
    private CampusProductMapper productMapper;

    @InjectMocks
    private CampusProductServiceImpl productService;

    private CampusProduct ownedProduct() {
        CampusProduct p = new CampusProduct();
        p.setId(PRODUCT_ID);
        p.setSellerId(SELLER_ID);
        p.setTitle("九成新 iPad");
        return p;
    }

    private CampusProduct othersProduct() {
        CampusProduct p = new CampusProduct();
        p.setId(200L);
        p.setSellerId(OTHER_ID);
        return p;
    }

    @Test
    @DisplayName("发布时服务端强制卖家身份，并给缺省 status 补上架")
    void insertForcesSellerIdentityAndDefaultsStatus() {
        CampusProduct product = new CampusProduct();
        product.setSellerId(OTHER_ID);
        product.setSellerName("伪造的卖家名");

        productService.insertCampusProduct(product, SELLER_ID, "张三");

        ArgumentCaptor<CampusProduct> captor = ArgumentCaptor.forClass(CampusProduct.class);
        verify(productMapper).insertCampusProduct(captor.capture());
        CampusProduct sent = captor.getValue();
        assertEquals(SELLER_ID, sent.getSellerId(), "sellerId 必须被服务端覆盖，不接受前端传值");
        assertEquals("张三", sent.getSellerName(), "sellerName 必须被服务端覆盖");
        assertEquals(Integer.valueOf(1), sent.getStatus(), "未传 status 时缺省上架");
    }

    @Test
    @DisplayName("发布时显式传入 status 则尊重调用方（如下架发布）")
    void insertKeepsExplicitStatus() {
        CampusProduct product = new CampusProduct();
        product.setStatus(0);

        productService.insertCampusProduct(product, SELLER_ID, "张三");

        ArgumentCaptor<CampusProduct> captor = ArgumentCaptor.forClass(CampusProduct.class);
        verify(productMapper).insertCampusProduct(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("归属人可以修改自己的商品")
    void updateAsOwnerSucceeds() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(ownedProduct());

        productService.updateCampusProduct(ownedProduct(), SELLER_ID);

        verify(productMapper).updateCampusProduct(any(CampusProduct.class));
    }

    @Test
    @DisplayName("管理员（userId=1）可以修改他人商品")
    void updateAsAdminSucceeds() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(ownedProduct());

        productService.updateCampusProduct(ownedProduct(), ADMIN_ID);

        verify(productMapper).updateCampusProduct(any(CampusProduct.class));
    }

    @Test
    @DisplayName("负向：非归属人修改被拒，且不触达 mapper 写操作")
    void updateByNonOwnerRejected() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(ownedProduct());

        assertThrows(ServiceException.class,
                () -> productService.updateCampusProduct(ownedProduct(), OTHER_ID));
        verify(productMapper, never()).updateCampusProduct(any(CampusProduct.class));
    }

    @Test
    @DisplayName("负向：修改不存在的商品被拒")
    void updateMissingProductRejected() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> productService.updateCampusProduct(ownedProduct(), SELLER_ID));
        verify(productMapper, never()).updateCampusProduct(any(CampusProduct.class));
    }

    @Test
    @DisplayName("批量删除：ids 为 null 时零删除")
    void deleteByIdsIgnoresNullArray() {
        int rows = productService.deleteCampusProductByIds(null, SELLER_ID);

        assertEquals(0, rows);
        verify(productMapper, never()).deleteCampusProductById(any());
    }

    @Test
    @DisplayName("批量删除：只删本人商品，他人商品/已删商品/null 元素跳过")
    void deleteByIdsOnlyDeletesOwn() {
        when(productMapper.selectCampusProductById(100L)).thenReturn(ownedProduct());
        when(productMapper.selectCampusProductById(200L)).thenReturn(othersProduct());
        when(productMapper.selectCampusProductById(300L)).thenReturn(null);
        // mockito 对未 stub 的 int 返回缺省 0，须显式 stub 才能断言"计入了删除数"
        when(productMapper.deleteCampusProductById(100L)).thenReturn(1);

        int rows = productService.deleteCampusProductByIds(
                new Long[] { 100L, 200L, 300L, null }, SELLER_ID);

        assertEquals(1, rows, "只有 100L 是本人的商品");
        verify(productMapper).deleteCampusProductById(100L);
        verify(productMapper, never()).deleteCampusProductById(200L);
        verify(productMapper, never()).deleteCampusProductById(300L);
    }

    @Test
    @DisplayName("负向：调用方 userId 为 null 时批量删除零删除")
    void deleteByIdsNullUserDeletesNothing() {
        int rows = productService.deleteCampusProductByIds(new Long[] { PRODUCT_ID }, null);

        assertEquals(0, rows);
        verify(productMapper, never()).deleteCampusProductById(any());
    }

    @Test
    @DisplayName("上下架：归属人操作时更新对象只带 id 与 status")
    void changeStatusAsOwnerUpdatesOnlyStatus() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(ownedProduct());

        productService.changeStatus(PRODUCT_ID, 0, SELLER_ID);

        ArgumentCaptor<CampusProduct> captor = ArgumentCaptor.forClass(CampusProduct.class);
        verify(productMapper).updateCampusProduct(captor.capture());
        CampusProduct sent = captor.getValue();
        assertEquals(PRODUCT_ID, sent.getId());
        assertEquals(Integer.valueOf(0), sent.getStatus());
    }

    @Test
    @DisplayName("负向：非归属人上下架被拒")
    void changeStatusUnauthorizedRejected() {
        when(productMapper.selectCampusProductById(PRODUCT_ID)).thenReturn(ownedProduct());

        assertThrows(ServiceException.class,
                () -> productService.changeStatus(PRODUCT_ID, 0, OTHER_ID));
        verify(productMapper, never()).updateCampusProduct(any(CampusProduct.class));
    }
}
