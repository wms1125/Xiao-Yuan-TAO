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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.dto.CampusProductCreateDTO;
import com.ruoyi.campus.service.ICampusProductService;

/**
 * 二手商品 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/product")
public class CampusProductController extends BaseController {
    @Autowired
    private ICampusProductService productService;

    @Anonymous
    @GetMapping("/list")
    public TableDataInfo list(CampusProduct product) {
        startPage();
        List<CampusProduct> list = productService.selectCampusProductList(product);
        return getDataTable(list);
    }

    @Anonymous
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(productService.selectCampusProductById(id));
    }

    @PreAuthorize("@ss.hasPermi('campus:product:add')")
    @PostMapping
    public AjaxResult add(@Valid @RequestBody CampusProductCreateDTO body) {
        CampusProduct product = new CampusProduct();
        product.setTitle(body.getTitle());
        product.setCategoryId(body.getCategoryId());
        product.setPrice(body.getPrice());
        product.setOriginalPrice(body.getOriginalPrice());
        product.setConditionLevel(body.getConditionLevel());
        product.setStock(body.getStock());
        product.setStatus(body.getStatus());
        product.setDescription(body.getDescription());
        product.setImages(body.getImages());
        // 卖家身份取自登录态，服务端强制写入
        return AjaxResult.success(productService.insertCampusProduct(product,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('campus:product:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody CampusProduct body) {
        // 仅允许修改可编辑字段，sellerId/sellerName 不接受前端传入，防止篡改归属
        CampusProduct product = new CampusProduct();
        product.setId(body.getId());
        product.setCategoryId(body.getCategoryId());
        product.setTitle(body.getTitle());
        product.setPrice(body.getPrice());
        product.setOriginalPrice(body.getOriginalPrice());
        product.setConditionLevel(body.getConditionLevel());
        product.setDescription(body.getDescription());
        product.setImages(body.getImages());
        product.setStock(body.getStock());
        product.setStatus(body.getStatus());
        return AjaxResult.success(productService.updateCampusProduct(product, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('campus:product:remove')")
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return AjaxResult.success(productService.deleteCampusProductByIds(ids, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('campus:product:edit')")
    @PutMapping("/status")
    public AjaxResult changeStatus(@RequestBody CampusProduct product) {
        return AjaxResult.success(
                productService.changeStatus(product.getId(), product.getStatus(), SecurityUtils.getUserId()));
    }
}
