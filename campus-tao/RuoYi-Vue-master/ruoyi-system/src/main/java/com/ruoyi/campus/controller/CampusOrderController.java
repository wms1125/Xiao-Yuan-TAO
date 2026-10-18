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
import com.ruoyi.common.core.page.TableDataInfo;
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
                body.getReceiverName(), body.getReceiverPhone(), body.getAddress(),
                body.getAddressId(), body.getRemark());
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

    /** 收款码直付请求体：买家可选上传付款截图 */
    public static class DirectPayDTO {
        /** 付款截图 URL（可空，经 /common/upload 上传得到） */
        private String payProofUrl;

        public String getPayProofUrl() { return payProofUrl; }
        public void setPayProofUrl(String payProofUrl) { this.payProofUrl = payProofUrl; }
    }

    /**
     * C2C 直付：买家声明已扫码付款给卖家（0→待卖家确认收款）。
     * 平台不经手资金，此处仅是状态撮合；卖家确认后才进入已付款。
     */
    @PreAuthorize("@ss.hasPermi('campus:order:edit')")
    @Log(title = "校园淘订单", businessType = BusinessType.UPDATE)
    @PostMapping("/direct-pay/{orderNo}")
    public AjaxResult directPay(@PathVariable String orderNo,
                                @RequestBody(required = false) DirectPayDTO body) {
        orderService.initiateDirectPay(orderNo, SecurityUtils.getUserId(),
                body == null ? null : body.getPayProofUrl());
        return AjaxResult.success("已通知卖家确认收款");
    }

    /** C2C 直付：卖家确认已收到买家转账（待卖家确认→已付款） */
    @PreAuthorize("@ss.hasPermi('campus:order:edit')")
    @Log(title = "校园淘订单", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm-pay/{orderNo}")
    public AjaxResult confirmPay(@PathVariable String orderNo) {
        orderService.confirmDirectPay(orderNo, SecurityUtils.getUserId());
        return AjaxResult.success("已确认收款");
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

    /** 我的订单：登录后仅可见自己的订单；管理员可见全部（管理端）。分页返回，避免列表无界增长 */
    @GetMapping("/list")
    public TableDataInfo list(CampusOrder order) {
        Long userId = SecurityUtils.getUserId();
        if (!SecurityUtils.isAdmin(userId)) {
            order.setUserId(userId);
        }
        startPage();
        List<CampusOrder> list = orderService.selectList(order);
        return getDataTable(list);
    }

    /**
     * 列表页概览：按状态分组统计（与列表使用同一筛选条件）。
     * 分页后前端不能再遍历当前页推导概览数字，故由服务端聚合返回。
     */
    @GetMapping("/stats")
    public AjaxResult stats(CampusOrder order) {
        Long userId = SecurityUtils.getUserId();
        if (!SecurityUtils.isAdmin(userId)) {
            order.setUserId(userId);
        }
        return AjaxResult.success(orderService.countGroupByStatus(order));
    }

    /** 订单详情：仅订单归属人（买家/卖家，V8 直付后卖家需核对付款）或管理员可查看 */
    @GetMapping("/{orderNo}")
    public AjaxResult detail(@PathVariable String orderNo) {
        Long userId = SecurityUtils.getUserId();
        CampusOrder order = orderService.selectWithItems(orderNo);
        boolean owner = order != null
                && (userId.equals(order.getUserId()) || userId.equals(order.getSellerId()));
        if (order == null || (!owner && !SecurityUtils.isAdmin(userId))) {
            return AjaxResult.error("无权查看该订单");
        }
        return AjaxResult.success(order);
    }
}
