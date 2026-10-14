package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusAftersale;
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
}
