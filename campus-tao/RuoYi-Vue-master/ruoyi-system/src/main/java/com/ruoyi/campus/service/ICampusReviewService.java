package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusReview;
import java.util.List;

/**
 * 评价服务（校园淘）
 */
public interface ICampusReviewService {
    /** 提交评价（订单需已完成且未评价过） */
    void addReview(Long userId, String userName, Long orderId, Long productId,
                   Integer rating, String content, String images);
    List<CampusReview> selectByProductId(Long productId);
    CampusReview selectByOrderAndProduct(Long orderId, Long productId);
}
