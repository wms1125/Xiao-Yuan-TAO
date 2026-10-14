package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单项 Mapper（校园淘）
 */
@Mapper
public interface CampusOrderItemMapper {
    int insertCampusOrderItem(CampusOrderItem item);
    int insertCampusOrderItemBatch(List<CampusOrderItem> items);
    List<CampusOrderItem> selectByOrderNo(String orderNo);
    List<CampusOrderItem> selectByOrderId(Long orderId);
}
