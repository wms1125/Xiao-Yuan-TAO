package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper（校园淘）
 */
@Mapper
public interface CampusCartMapper {
    int insertCampusCart(CampusCart cart);
    int updateCampusCart(CampusCart cart);
    int deleteCampusCartById(Long id);
    int deleteCartByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);
    CampusCart selectCartById(Long id);
    CampusCart selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);
    List<CampusCart> selectCartList(CampusCart cart);
    int countByUserId(Long userId);
}
