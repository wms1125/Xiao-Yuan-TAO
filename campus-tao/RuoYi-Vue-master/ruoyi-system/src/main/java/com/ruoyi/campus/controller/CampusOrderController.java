package com.ruoyi.campus.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.dto.CampusOrderCreateDTO;
import com.ruoyi.campus.service.ICampusOrderService;

/**
 * 订单 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/order")
public class CampusOrderController extends BaseController {
    @Autowired
    private ICampusOrderService orderService;

    @PreAuthorize("@ss.hasPermi('campus:order:add')")
    @Log(title = "校园淘订单", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public AjaxResult create(@Valid @RequestBody CampusOrderCreateDTO body) {
        Long userId = SecurityUtils.getUserId();
        Long[] cartIds = body.getCartIds().stream().map(Long::valueOf).toArray(Long[]::new);
        String orderNo = orderService.createOrder(userId, SecurityUtils.getUsername(), cartIds,
                body.getReceiverName(), body.getReceiverPhone(), body.getAddress(), body.getRemark());
        return AjaxResult.success("下单成功", orderNo);
    }

    @PreAuthorize("@ss.hasPermi('campus:order:edit')")
    @Log(title = "校园淘订单", businessType = BusinessType.UPDATE)
    @PostMapping("/pay/{orderNo}")
    public AjaxResult pay(@PathVariable String orderNo) {
        boolean ok = orderService.payOrder(orderNo, SecurityUtils.getUserId());
        if (!ok) {
            return AjaxResult.error("沙箱余额不足，请先充值");
        }
        return AjaxResult.success("支付成功");
    }

    @PreAuthorize("@ss.hasPermi('campus:order:edit')")
    @Log(title = "校园淘订单", businessType = BusinessType.UPDATE)
    @PostMapping("/cancel/{orderNo}")
    public AjaxResult cancel(@PathVariable String orderNo) {
        orderService.cancelOrder(orderNo, SecurityUtils.getUserId());
        return AjaxResult.success("已取消");
    }

    /** 发货（管理端，可录入物流单号） */
    @PreAuthorize("@ss.hasPermi('campus:order:edit')")
    @Log(title = "校园淘订单", businessType = BusinessType.UPDATE)
    @PostMapping("/ship/{orderNo}")
    public AjaxResult ship(@PathVariable String orderNo, @RequestParam(required = false) String logisticsNo) {
        orderService.shipOrder(orderNo, logisticsNo);
        return AjaxResult.success("已发货");
    }

    /** 确认收货（买家） */
    @PreAuthorize("@ss.hasPermi('campus:order:edit')")
    @Log(title = "校园淘订单", businessType = BusinessType.UPDATE)
    @PostMapping("/finish/{orderNo}")
    public AjaxResult finish(@PathVariable String orderNo) {
        orderService.finishOrder(orderNo, SecurityUtils.getUserId());
        return AjaxResult.success("已确认收货");
    }

    /** 我的订单：登录后仅可见自己的订单；管理员可见全部（管理端） */
    @GetMapping("/list")
    public AjaxResult list(CampusOrder order) {
        Long userId = SecurityUtils.getUserId();
        if (!SecurityUtils.isAdmin(userId)) {
            order.setUserId(userId);
        }
        return AjaxResult.success(orderService.selectList(order));
    }

    /** 订单详情：仅订单归属人（或管理员）可查看 */
    @GetMapping("/{orderNo}")
    public AjaxResult detail(@PathVariable String orderNo) {
        Long userId = SecurityUtils.getUserId();
        CampusOrder order = orderService.selectWithItems(orderNo);
        if (order == null || (!userId.equals(order.getUserId()) && !SecurityUtils.isAdmin(userId))) {
            return AjaxResult.error("无权查看该订单");
        }
        return AjaxResult.success(order);
    }
}
