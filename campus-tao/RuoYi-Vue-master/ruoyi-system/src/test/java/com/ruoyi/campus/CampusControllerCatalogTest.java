package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

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

import com.ruoyi.campus.controller.CampusCategoryController;
import com.ruoyi.campus.controller.CampusNotifyLogController;
import com.ruoyi.campus.controller.CampusProductController;
import com.ruoyi.campus.controller.CampusReviewController;
import com.ruoyi.campus.domain.CampusCategory;
import com.ruoyi.campus.domain.CampusNotifyLog;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.domain.CampusReview;
import com.ruoyi.campus.dto.CampusProductCreateDTO;
import com.ruoyi.campus.dto.CampusReviewAddDTO;
import com.ruoyi.campus.service.ICampusCategoryService;
import com.ruoyi.campus.service.ICampusNotifyLogService;
import com.ruoyi.campus.service.ICampusProductService;
import com.ruoyi.campus.service.ICampusReviewService;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * Controller 层测试：分类（含分页）/ 商品（卖家身份强制）/ 评价 / 外呼留痕。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 Controller：分类 / 商品 / 评价 / 外呼留痕")
class CampusControllerCatalogTest {

    private static final long STUDENT_ID = 2L;
    private static final String STUDENT_NAME = "ry";

    @Mock
    private ICampusCategoryService categoryService;
    @Mock
    private ICampusProductService productService;
    @Mock
    private ICampusReviewService reviewService;
    @Mock
    private ICampusNotifyLogService notifyLogService;

    @InjectMocks
    private CampusCategoryController categoryController;
    @InjectMocks
    private CampusProductController productController;
    @InjectMocks
    private CampusReviewController reviewController;
    @InjectMocks
    private CampusNotifyLogController notifyLogController;

    @Captor
    private ArgumentCaptor<CampusProduct> productCaptor;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, STUDENT_NAME);
        CampusTestSupport.bindRequest();
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    // ---------------- 分类 ----------------

    @Test
    @DisplayName("分类列表：分页包装（匿名可访问）")
    void categoryListReturnsTableData() {
        List<CampusCategory> list = Collections.singletonList(new CampusCategory());
        when(categoryService.selectCampusCategoryList(any())).thenReturn(list);

        TableDataInfo table = categoryController.list(new CampusCategory());

        assertEquals(HttpStatus.SUCCESS, table.getCode());
        assertSame(list, table.getRows());
        assertEquals(1L, table.getTotal());
    }

    @Test
    @DisplayName("分类详情")
    void categoryGetInfo() {
        CampusCategory category = new CampusCategory();
        category.setId(1L);
        when(categoryService.selectCampusCategoryById(1L)).thenReturn(category);

        AjaxResult result = categoryController.getInfo(1L);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(category, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("分类新增/修改/删除：委派服务层")
    void categoryCrudDelegate() {
        CampusCategory category = new CampusCategory();
        category.setId(3L);
        category.setName("教材书籍");

        categoryController.add(category);
        categoryController.edit(category);
        categoryController.remove(3L);

        verify(categoryService).insertCampusCategory(category);
        verify(categoryService).updateCampusCategory(category);
        verify(categoryService).deleteCampusCategoryById(3L);
    }

    // ---------------- 商品 ----------------

    @Test
    @DisplayName("商品列表：分页包装（匿名可访问）")
    void productListReturnsTableData() {
        List<CampusProduct> list = Collections.singletonList(new CampusProduct());
        when(productService.selectCampusProductList(any())).thenReturn(list);

        TableDataInfo table = productController.list(new CampusProduct());

        assertEquals(HttpStatus.SUCCESS, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    @DisplayName("商品详情")
    void productGetInfo() {
        CampusProduct product = new CampusProduct();
        product.setId(5L);
        when(productService.selectCampusProductById(5L)).thenReturn(product);

        AjaxResult result = productController.getInfo(5L);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(product, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("发布商品：DTO 字段映射，卖家身份取自登录态且不接受前端传入")
    void productAddForcesSellerFromLogin() {
        CampusProductCreateDTO body = new CampusProductCreateDTO();
        body.setTitle("二手教材 Java 编程思想");
        body.setCategoryId(1L);
        body.setPrice(new BigDecimal("29.90"));
        body.setOriginalPrice(new BigDecimal("89.00"));
        body.setConditionLevel(8);
        body.setStock(3);
        body.setStatus(1);
        body.setDescription("九成新");
        body.setImages("/campus-img/book.jpg");

        productController.add(body);

        verify(productService).insertCampusProduct(productCaptor.capture(), eq(STUDENT_ID), eq(STUDENT_NAME));
        CampusProduct passed = productCaptor.getValue();
        assertEquals("二手教材 Java 编程思想", passed.getTitle());
        assertEquals(1L, passed.getCategoryId());
        assertEquals(new BigDecimal("29.90"), passed.getPrice());
        assertEquals(3, passed.getStock());
        // 关键断言：服务端写入的卖家身份不由前端控制
        assertNull(passed.getSellerId());
        assertNull(passed.getSellerName());
    }

    @Test
    @DisplayName("修改商品：丢弃前端传入的 sellerId/sellerName，仅透传可编辑字段")
    void productEditDropsForgedSeller() {
        CampusProduct body = new CampusProduct();
        body.setId(5L);
        body.setTitle("改名后的商品");
        body.setStock(9);
        // 伪造归属：前端尝试把商品挂到别人名下
        body.setSellerId(99L);
        body.setSellerName("forged-hacker");

        productController.edit(body);

        verify(productService).updateCampusProduct(productCaptor.capture(), eq(STUDENT_ID));
        CampusProduct passed = productCaptor.getValue();
        assertEquals(5L, passed.getId());
        assertEquals("改名后的商品", passed.getTitle());
        assertEquals(9, passed.getStock());
        assertNull(passed.getSellerId());
        assertNull(passed.getSellerName());
    }

    @Test
    @DisplayName("删除商品：归属校验参数取自登录态")
    void productRemoveUsesLoginUser() {
        Long[] ids = new Long[] { 5L, 6L };

        productController.remove(ids);

        verify(productService).deleteCampusProductByIds(eq(ids), eq(STUDENT_ID));
    }

    @Test
    @DisplayName("上下架：归属校验参数取自登录态")
    void productChangeStatusUsesLoginUser() {
        CampusProduct body = new CampusProduct();
        body.setId(5L);
        body.setStatus(0);

        productController.changeStatus(body);

        verify(productService).changeStatus(5L, 0, STUDENT_ID);
    }

    // ---------------- 评价 ----------------

    @Test
    @DisplayName("发表评价：评价人取自登录态")
    void reviewAddUsesLoginUser() {
        CampusReviewAddDTO body = new CampusReviewAddDTO();
        body.setOrderId(11L);
        body.setProductId(5L);
        body.setRating(5);
        body.setContent("书很新，卖家很赞");
        body.setImages("/campus-img/book.jpg");

        AjaxResult result = reviewController.add(body);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        verify(reviewService).addReview(STUDENT_ID, STUDENT_NAME, 11L, 5L, 5, "书很新，卖家很赞", "/campus-img/book.jpg");
    }

    @Test
    @DisplayName("商品评价列表：匿名可访问")
    void reviewListByProduct() {
        List<CampusReview> list = Collections.singletonList(new CampusReview());
        when(reviewService.selectByProductId(5L)).thenReturn(list);

        AjaxResult result = reviewController.listByProduct(5L);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(list, result.get(AjaxResult.DATA_TAG));
    }

    // ---------------- 外呼留痕（运营端） ----------------

    @Test
    @DisplayName("外呼留痕列表：过滤条件透传")
    void notifyLogListPassesFilters() {
        List<CampusNotifyLog> list = Collections.singletonList(new CampusNotifyLog());
        when(notifyLogService.selectList("sms", 1, STUDENT_ID, "CT202601010001", 50)).thenReturn(list);

        AjaxResult result = notifyLogController.list("sms", 1, STUDENT_ID, "CT202601010001", 50);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertSame(list, result.get(AjaxResult.DATA_TAG));
    }
}
