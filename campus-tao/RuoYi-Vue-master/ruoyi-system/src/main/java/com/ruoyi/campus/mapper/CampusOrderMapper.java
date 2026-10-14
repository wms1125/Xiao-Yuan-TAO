package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单 Mapper（校园淘）
 */
@Mapper
public interface CampusOrderMapper {
    int insertCampusOrder(CampusOrder order);
    int updateCampusOrder(CampusOrder order);
    CampusOrder selectOrderByOrderNo(String orderNo);
    CampusOrder selectOrderById(Long id);
    List<CampusOrder> selectOrderList(CampusOrder order);
    int countByUserId(@Param("userId") Long userId);
}
