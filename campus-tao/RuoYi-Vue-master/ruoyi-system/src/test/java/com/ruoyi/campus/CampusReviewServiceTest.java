package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusReview;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusReviewMapper;
import com.ruoyi.campus.service.impl.CampusReviewServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：评价提交前置校验与卖家视角评价查询。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 Service：评价提交与卖家评价查询")
class CampusReviewServiceTest {

    private static final long BUYER_ID = 2L;
    private static final long SELLER_ID = 9L;
    private static final long ORDER_ID = 88L;
    private static final long PRODUCT_ID = 100L;
    private static final int STATUS_FINISHED = 3;

    @Mock
    private CampusReviewMapper reviewMapper;
    @Mock
    private CampusOrderMapper orderMapper;

    @InjectMocks
    private CampusReviewServiceImpl reviewService;

    @Captor
    private ArgumentCaptor<CampusReview> reviewCaptor;

    private CampusOrder finishedOrder(long userId) {
        CampusOrder order = new CampusOrder();
        order.setId(ORDER_ID);
        order.setUserId(userId);
        order.setStatus(STATUS_FINISHED);
        return order;
    }

    @Test
    @DisplayName("卖家评价查询：按登录用户 ID 委托 mapper，并原样返回（含商品标题）")
    void selectBySellerIdDelegatesToMapper() {
        CampusReview row = new CampusReview();
        row.setId(1L);
        row.setProductTitle("二手自行车");
        List<CampusReview> expected = Collections.singletonList(row);
        when(reviewMapper.selectReviewListBySellerId(SELLER_ID)).thenReturn(expected);

        List<CampusReview> actual = reviewService.selectBySellerId(SELLER_ID);

        assertSame(expected, actual);
        assertEquals("二手自行车", actual.get(0).getProductTitle());
        verify(reviewMapper).selectReviewListBySellerId(SELLER_ID);
    }

    @Test
    @DisplayName("按商品查询：委托 mapper")
    void selectByProductIdDelegatesToMapper() {
        when(reviewMapper.selectByProductId(PRODUCT_ID)).thenReturn(Collections.emptyList());

        assertEquals(0, reviewService.selectByProductId(PRODUCT_ID).size());
        verify(reviewMapper).selectByProductId(PRODUCT_ID);
    }

    @Test
    @DisplayName("提交评价：订单不存在时拒绝")
    void addReviewRejectsMissingOrder() {
        when(orderMapper.selectOrderById(ORDER_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> reviewService.addReview(BUYER_ID, "ry", ORDER_ID, PRODUCT_ID, 5, "很好", ""));

        assertEquals("订单不存在", ex.getMessage());
        verify(reviewMapper, never()).insertCampusReview(any());
    }

    @Test
    @DisplayName("提交评价：订单不属于当前用户时拒绝（防越权评价他人订单）")
    void addReviewRejectsOtherUsersOrder() {
        when(orderMapper.selectOrderById(ORDER_ID)).thenReturn(finishedOrder(999L));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> reviewService.addReview(BUYER_ID, "ry", ORDER_ID, PRODUCT_ID, 5, "很好", ""));

        assertEquals("订单不存在", ex.getMessage());
        verify(reviewMapper, never()).insertCampusReview(any());
    }

    @Test
    @DisplayName("提交评价：订单未完成时拒绝")
    void addReviewRejectsUnfinishedOrder() {
        CampusOrder order = finishedOrder(BUYER_ID);
        order.setStatus(2);
        when(orderMapper.selectOrderById(ORDER_ID)).thenReturn(order);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> reviewService.addReview(BUYER_ID, "ry", ORDER_ID, PRODUCT_ID, 5, "很好", ""));

        assertEquals("订单未完成，无法评价", ex.getMessage());
        verify(reviewMapper, never()).insertCampusReview(any());
    }

    @Test
    @DisplayName("提交评价：同一订单同一商品重复评价时拒绝")
    void addReviewRejectsDuplicate() {
        when(orderMapper.selectOrderById(ORDER_ID)).thenReturn(finishedOrder(BUYER_ID));
        when(reviewMapper.selectByOrderAndProduct(ORDER_ID, PRODUCT_ID)).thenReturn(new CampusReview());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> reviewService.addReview(BUYER_ID, "ry", ORDER_ID, PRODUCT_ID, 5, "很好", ""));

        assertEquals("该商品已评价", ex.getMessage());
        verify(reviewMapper, never()).insertCampusReview(any());
    }

    @Test
    @DisplayName("提交评价：校验通过时落库，卖家与订单信息取自入参而非前端伪造")
    void addReviewPersistsWhenValid() {
        when(orderMapper.selectOrderById(ORDER_ID)).thenReturn(finishedOrder(BUYER_ID));
        when(reviewMapper.selectByOrderAndProduct(ORDER_ID, PRODUCT_ID)).thenReturn(null);

        reviewService.addReview(BUYER_ID, "ry", ORDER_ID, PRODUCT_ID, 4, "成色不错", "a.jpg");

        verify(reviewMapper).insertCampusReview(reviewCaptor.capture());
        CampusReview saved = reviewCaptor.getValue();
        assertEquals(ORDER_ID, saved.getOrderId());
        assertEquals(PRODUCT_ID, saved.getProductId());
        assertEquals(BUYER_ID, saved.getUserId());
        assertEquals("ry", saved.getUserName());
        assertEquals(4, saved.getRating());
        assertEquals("成色不错", saved.getContent());
        assertEquals("a.jpg", saved.getImages());
    }

    @Test
    @DisplayName("提交评价：未传评分时按 5 分兜底")
    void addReviewDefaultsRatingToFive() {
        when(orderMapper.selectOrderById(ORDER_ID)).thenReturn(finishedOrder(BUYER_ID));
        when(reviewMapper.selectByOrderAndProduct(ORDER_ID, PRODUCT_ID)).thenReturn(null);

        reviewService.addReview(BUYER_ID, "ry", ORDER_ID, PRODUCT_ID, null, null, null);

        verify(reviewMapper).insertCampusReview(reviewCaptor.capture());
        assertEquals(5, reviewCaptor.getValue().getRating());
    }
}
