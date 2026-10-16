package com.ruoyi.campus.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.campus.domain.CampusCategory;
import com.ruoyi.campus.dto.CampusCategorySaveDTO;
import com.ruoyi.campus.service.ICampusCategoryService;

/**
 * 商品分类 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/category")
public class CampusCategoryController extends BaseController {
    @Autowired
    private ICampusCategoryService categoryService;

    @Anonymous
    @GetMapping("/list")
    public TableDataInfo list(CampusCategory category) {
        startPage();
        List<CampusCategory> list = categoryService.selectCampusCategoryList(category);
        return getDataTable(list);
    }

    @Anonymous
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(categoryService.selectCampusCategoryById(id));
    }

    @PreAuthorize("@ss.hasPermi('campus:category:add')")
    @PostMapping
    public AjaxResult add(@Valid @RequestBody CampusCategorySaveDTO body) {
        return AjaxResult.success(categoryService.insertCampusCategory(toEntity(body)));
    }

    @PreAuthorize("@ss.hasPermi('campus:category:edit')")
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CampusCategorySaveDTO body) {
        return AjaxResult.success(categoryService.updateCampusCategory(toEntity(body)));
    }

    @PreAuthorize("@ss.hasPermi('campus:category:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return AjaxResult.success(categoryService.deleteCampusCategoryById(id));
    }

    /** DTO → 实体（只搬运可编辑字段，避免前端注入 createTime 等非业务字段） */
    private CampusCategory toEntity(CampusCategorySaveDTO body) {
        CampusCategory category = new CampusCategory();
        category.setId(body.getId());
        category.setName(body.getName());
        category.setParentId(body.getParentId());
        category.setSort(body.getSort());
        return category;
    }
}
