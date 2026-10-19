package com.ruoyi.campus.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.security.SelfServiceEndpoint;
import com.ruoyi.campus.service.ICampusLogisticsService;
import com.ruoyi.campus.service.ICampusOrderService;

/**
 * 物流轨迹 Controller（校园淘）
 *
 * 轨迹由可配置的承运商 Provider 拉取；未配置时自动降级（providerEnabled=false）。
 * 查询接口强制登录并校验订单归属，与订单详情保持一致的越权防护口径。
 */
@RestController
@RequestMapping("/campus/logistics")
public class CampusLogisticsController extends BaseController {

    @Autowired
    private ICampusLogisticsService logisticsService;

    @Autowired
    private ICampusOrderService orderService;

    /** 物流轨迹 Provider 是否已启用（前端据此决定是否展示轨迹区块） */
    @GetMapping("/status")
    public AjaxResult status() {
        return AjaxResult.success(logisticsService.providerEnabled());
    }

    /** 订单物流轨迹：仅订单归属人（或管理员）可查看 */
    @GetMapping("/trace/{orderNo}")
    public AjaxResult trace(@PathVariable String orderNo) {
        CampusOrder order = orderService.selectWithItems(orderNo);
        if (!canAccess(order)) {
            return AjaxResult.error("无权查看该订单物流轨迹");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("providerEnabled", logisticsService.providerEnabled());
        data.put("logisticsNo", order.getLogisticsNo());
        data.put("traces", logisticsService.listTrace(orderNo));
        return AjaxResult.success(data);
    }

    /** 手工触发轨迹同步（管理员、买家或卖家），返回新增节点数 */
    @SelfServiceEndpoint("仅订单买家/卖家本人或管理员：方法内 canAccess 校验订单归属，非本人一律拒绝")
    @Log(title = "校园淘物流", businessType = BusinessType.UPDATE)
    @PostMapping("/sync/{orderNo}")
    public AjaxResult sync(@PathVariable String orderNo) {
        CampusOrder order = orderService.selectWithItems(orderNo);
        if (!canAccess(order)) {
            return AjaxResult.error("无权操作该订单物流轨迹");
        }
        if (!logisticsService.providerEnabled()) {
            return AjaxResult.error("物流轨迹查询通道未配置，已降级为仅记录物流单号");
        }
        int added = logisticsService.syncTrace(orderNo);
        return AjaxResult.success("已同步物流轨迹，新增 " + added + " 条节点");
    }

    /** 订单存在且为买家本人、卖家本人（或当前登录人为管理员）才可访问 */
    private boolean canAccess(CampusOrder order) {
        if (order == null) {
            return false;
        }
        Long userId = SecurityUtils.getUserId();
        return userId.equals(order.getUserId())
                || userId.equals(order.getSellerId())
                || SecurityUtils.isAdmin(userId);
    }
}
