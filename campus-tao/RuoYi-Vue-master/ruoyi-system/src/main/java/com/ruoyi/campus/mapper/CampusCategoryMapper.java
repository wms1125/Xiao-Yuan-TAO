package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品分类 Mapper（校园淘）
 */
@Mapper
public interface CampusCategoryMapper {
    int insertCampusCategory(CampusCategory category);

    int updateCampusCategory(CampusCategory category);

    int deleteCampusCategoryById(Long id);

    CampusCategory selectCampusCategoryById(Long id);

    List<CampusCategory> selectCampusCategoryList(CampusCategory category);
}
