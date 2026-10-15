package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusLogisticsTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 物流轨迹 Mapper（校园淘）
 */
@Mapper
public interface CampusLogisticsTraceMapper {

    /** 批量插入轨迹节点，命中唯一键的重复节点自动忽略（幂等同步） */
    int insertIgnoreBatch(@Param("list") List<CampusLogisticsTrace> list);

    /** 按订单号查询轨迹，按发生时间正序 */
    List<CampusLogisticsTrace> selectByOrderNo(@Param("orderNo") String orderNo);
}
