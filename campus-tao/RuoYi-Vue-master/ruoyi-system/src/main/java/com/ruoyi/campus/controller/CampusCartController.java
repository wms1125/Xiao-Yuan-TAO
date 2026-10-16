package com.ruoyi.campus.controller;

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
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.dto.CampusCartAddDTO;
import com.ruoyi.campus.dto.CampusCartCheckDTO;
import com.ruoyi.campus.dto.CampusCartQuantityDTO;
import com.ruoyi.campus.service.ICampusCartService;

/**
 * 购物车 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/cart")
public class CampusCartController extends BaseController {
    @Autowired
    private ICampusCartService cartService;

    /** 我的购物车：登录后仅返回本人购物车 */
    @GetMapping("/list")
    public AjaxResult list() {
        Long userId = SecurityUtils.getUserId();
        return AjaxResult.success(cartService.selectListByUserId(userId));
    }

    @PreAuthorize("@ss.hasPermi('campus:cart:add')")
    @PostMapping("/add")
    public AjaxResult add(@Valid @RequestBody CampusCartAddDTO body) {
        cartService.addToCart(SecurityUtils.getUserId(), SecurityUtils.getUsername(),
                body.getProductId(), body.getQuantity());
        return AjaxResult.success("已加入购物车");
    }

    @PreAuthorize("@ss.hasPermi('campus:cart:edit')")
    @PutMapping("/quantity")
    public AjaxResult updateQuantity(@Valid @RequestBody CampusCartQuantityDTO body) {
        return AjaxResult.success(
                cartService.updateQuantity(body.getId(), body.getQuantity(), SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('campus:cart:edit')")
    @PutMapping("/check")
    public AjaxResult toggleChecked(@Valid @RequestBody CampusCartCheckDTO body) {
        return AjaxResult.success(
                cartService.toggleChecked(body.getId(), body.getChecked(), SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('campus:cart:remove')")
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return AjaxResult.success(cartService.removeByIds(ids, SecurityUtils.getUserId()));
    }
}
