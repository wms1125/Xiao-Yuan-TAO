package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 售后 Mapper（校园淘）
 */
@Mapper
public interface CampusAftersaleMapper {
    int insertCampusAftersale(CampusAftersale aftersale);
    int updateCampusAftersale(CampusAftersale aftersale);
    CampusAftersale selectByAftersaleNo(String aftersaleNo);
    List<CampusAftersale> selectAftersaleList(CampusAftersale aftersale);

    /** 按状态分组统计售后单数与退款金额（与 selectAftersaleList 使用同一筛选条件），供列表页概览使用 */
    List<CampusStatusStat> countGroupByStatus(CampusAftersale aftersale);
}
