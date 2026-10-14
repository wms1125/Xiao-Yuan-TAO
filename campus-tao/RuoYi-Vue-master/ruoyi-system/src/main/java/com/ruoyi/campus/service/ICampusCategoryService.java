package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusCategory;
import java.util.List;

/**
 * 商品分类 Service（校园淘）
 */
public interface ICampusCategoryService {
    int insertCampusCategory(CampusCategory category);

    int updateCampusCategory(CampusCategory category);

    int deleteCampusCategoryById(Long id);

    CampusCategory selectCampusCategoryById(Long id);

    List<CampusCategory> selectCampusCategoryList(CampusCategory category);
}
