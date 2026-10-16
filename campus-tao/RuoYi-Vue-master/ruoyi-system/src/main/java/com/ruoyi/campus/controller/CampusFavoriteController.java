package com.ruoyi.campus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.service.ICampusFavoriteService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品收藏 Controller（校园淘）
 *
 * <p>收藏是**买家私有数据**：所有接口的用户身份一律取自登录态
 * （{@link SecurityUtils#getUserId()}），不接受前端传 userId——
 * 否则把 {@code userId} 作为入参即可查看/操作他人收藏夹。</p>
 *
 * @author campus-tao
 */
@Validated
@RestController
@RequestMapping("/campus/favorite")
public class CampusFavoriteController extends BaseController {

    @Autowired
    private ICampusFavoriteService favoriteService;

    /** 我的收藏列表（含商品概要，最新在前） */
    @PreAuthorize("@ss.hasPermi('campus:favorite:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(favoriteService.selectListByUser(SecurityUtils.getUserId()));
    }

    /** 我的收藏总数 */
    @PreAuthorize("@ss.hasPermi('campus:favorite:list')")
    @GetMapping("/count")
    public AjaxResult count() {
        return AjaxResult.success(favoriteService.countByUser(SecurityUtils.getUserId()));
    }

    /**
     * 批量查询商品的收藏状态（商品卡片列表渲染"收藏/已收藏"用）。
     *
     * <p>不逐个商品发请求，避免 N+1；一次性把当前页所有商品 ID 传进来。</p>
     */
    @PreAuthorize("@ss.hasPermi('campus:favorite:list')")
    @PostMapping("/status")
    public AjaxResult status(@RequestBody List<Long> productIds) {
        Map<String, Object> data = new HashMap<>(2);
        data.put("favored", favoriteService.selectFavoredProductIds(SecurityUtils.getUserId(), productIds));
        return AjaxResult.success(data);
    }

    /**
     * 切换收藏状态。
     *
     * <p>返回 {@code favored=true/false} 表示切换后的状态，前端直接据此前翻图标，
     * 不需要再回查一次列表。</p>
     */
    @PreAuthorize("@ss.hasPermi('campus:favorite:add')")
    @PostMapping("/toggle")
    public AjaxResult toggle(@RequestParam("productId") Long productId) {
        boolean favored = favoriteService.toggle(SecurityUtils.getUserId(), productId);
        Map<String, Object> data = new HashMap<>(2);
        data.put("favored", favored);
        return AjaxResult.success(favored ? "已收藏" : "已取消收藏", data);
    }

    /** 取消收藏（按商品 ID） */
    @PreAuthorize("@ss.hasPermi('campus:favorite:remove')")
    @PostMapping("/remove")
    public AjaxResult remove(@RequestParam("productId") Long productId) {
        boolean removed = favoriteService.remove(SecurityUtils.getUserId(), productId);
        return AjaxResult.success(removed ? "已取消收藏" : "该商品未被收藏");
    }
}
