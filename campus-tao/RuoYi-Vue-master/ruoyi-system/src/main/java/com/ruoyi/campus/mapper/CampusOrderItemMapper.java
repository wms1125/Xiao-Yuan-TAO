package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusOrderItem;
import org.apache.ibatis.annotations.Mapper;

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

    /** 批量按订单号查询订单项：列表页用它替代逐单查询，消除 N+1 */
    List<CampusOrderItem> selectByOrderNos(List<String> orderNos);
}
