package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评价 Mapper（校园淘）
 */
@Mapper
public interface CampusReviewMapper {
    int insertCampusReview(CampusReview review);
    List<CampusReview> selectByProductId(Long productId);
    List<CampusReview> selectByOrderId(Long orderId);
    CampusReview selectByOrderAndProduct(@Param("orderId") Long orderId, @Param("productId") Long productId);
}
