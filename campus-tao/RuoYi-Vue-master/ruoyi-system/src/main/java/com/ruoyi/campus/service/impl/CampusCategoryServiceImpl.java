package com.ruoyi.campus.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.campus.domain.CampusCategory;
import com.ruoyi.campus.mapper.CampusCategoryMapper;
import com.ruoyi.campus.service.ICampusCategoryService;

@Service
public class CampusCategoryServiceImpl implements ICampusCategoryService {
    @Autowired
    private CampusCategoryMapper categoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertCampusCategory(CampusCategory category) {
        return categoryMapper.insertCampusCategory(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCampusCategory(CampusCategory category) {
        return categoryMapper.updateCampusCategory(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCampusCategoryById(Long id) {
        return categoryMapper.deleteCampusCategoryById(id);
    }

    @Override
    public CampusCategory selectCampusCategoryById(Long id) {
        return categoryMapper.selectCampusCategoryById(id);
    }

    @Override
    public List<CampusCategory> selectCampusCategoryList(CampusCategory category) {
        return categoryMapper.selectCampusCategoryList(category);
    }
}
