package com.ruoyi.campus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.service.ICampusUserPayQrService;

/**
 * 用户收款码 Controller（校园淘 · C2C 直付）
 *
 * <p>买家扫码直接付给卖家本人，平台不经手资金。收款码属于用户敏感资产，
 * 仅允许本人查看与设置；卖家码在下单支付弹窗中展示给买家（走订单详情接口附带）。
 */
@Validated
@RestController
@RequestMapping("/campus/payqr")
public class CampusUserPayQrController extends BaseController {
    @Autowired
    private ICampusUserPayQrService payQrService;

    /** 保存收款码请求体 */
    public static class PayQrSaveDTO {
        @NotBlank(message = "收款码图片不能为空")
        private String qrUrl;

        public String getQrUrl() { return qrUrl; }
        public void setQrUrl(String qrUrl) { this.qrUrl = qrUrl; }
    }

    /** 我的收款码：未上传返回 data=null（前端据此显示引导上传） */
    @GetMapping
    public AjaxResult my() {
        return AjaxResult.success(payQrService.getMyPayQr(SecurityUtils.getUserId()));
    }

    /** 保存/覆盖本人收款码（图片先经 /common/upload 上传，此处只存 URL）。
     *  复用账户编辑权限：收款码与沙箱余额同属用户资金资产。 */
    @PreAuthorize("@ss.hasPermi('campus:account:edit')")
    @Log(title = "校园淘收款码", businessType = BusinessType.UPDATE)
    @PostMapping
    public AjaxResult save(@jakarta.validation.Valid @RequestBody PayQrSaveDTO body) {
        payQrService.saveMyPayQr(SecurityUtils.getUserId(), body.getQrUrl());
        return AjaxResult.success("收款码已保存");
    }
}
