package com.ruoyi.campus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusAccount;
import com.ruoyi.campus.dto.CampusAccountRechargeDTO;
import com.ruoyi.campus.service.ICampusAccountService;

/**
 * 支付沙箱账户 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/account")
public class CampusAccountController extends BaseController {
    @Autowired
    private ICampusAccountService accountService;

    /** 我的余额：登录后查询本人沙箱账户 */
    @GetMapping("/balance")
    public AjaxResult balance() {
        Long userId = SecurityUtils.getUserId();
        CampusAccount acc = accountService.getOrCreate(userId, SecurityUtils.getUsername());
        return AjaxResult.success(acc);
    }

    /** 演示用：充值沙箱余额 */
    @PreAuthorize("@ss.hasPermi('campus:account:edit')")
    @Log(title = "校园淘账户", businessType = BusinessType.UPDATE)
    @PostMapping("/recharge")
    public AjaxResult recharge(@Valid @RequestBody CampusAccountRechargeDTO body) {
        accountService.refund(SecurityUtils.getUserId(), body.getAmount());
        return AjaxResult.success("充值成功");
    }
}
