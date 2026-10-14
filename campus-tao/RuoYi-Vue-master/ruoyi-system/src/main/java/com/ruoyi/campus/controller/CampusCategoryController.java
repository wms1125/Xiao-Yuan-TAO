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
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.campus.domain.CampusCategory;
import com.ruoyi.campus.service.ICampusCategoryService;

/**
 * 商品分类 Controller（校园淘）
 */
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
    public AjaxResult add(@RequestBody CampusCategory category) {
        return AjaxResult.success(categoryService.insertCampusCategory(category));
    }

    @PreAuthorize("@ss.hasPermi('campus:category:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody CampusCategory category) {
        return AjaxResult.success(categoryService.updateCampusCategory(category));
    }

    @PreAuthorize("@ss.hasPermi('campus:category:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return AjaxResult.success(categoryService.deleteCampusCategoryById(id));
    }
}
