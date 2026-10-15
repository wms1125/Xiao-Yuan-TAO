package com.ruoyi.campus.controller;

import java.util.List;
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
import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.dto.CampusAftersaleApplyDTO;
import com.ruoyi.campus.dto.CampusAftersaleAuditDTO;
import com.ruoyi.campus.service.ICampusAftersaleService;

/**
 * 售后 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/aftersale")
public class CampusAftersaleController extends BaseController {
    @Autowired
    private ICampusAftersaleService aftersaleService;

    @PreAuthorize("@ss.hasPermi('campus:aftersale:add')")
    @Log(title = "校园淘售后", businessType = BusinessType.INSERT)
    @PostMapping("/apply")
    public AjaxResult apply(@Valid @RequestBody CampusAftersaleApplyDTO body) {
        String no = aftersaleService.apply(SecurityUtils.getUserId(), SecurityUtils.getUsername(),
                body.getOrderNo(), body.getType(), body.getReason());
        return AjaxResult.success("申请已提交", no);
    }

    /** 审核（管理端） */
    @PreAuthorize("@ss.hasPermi('campus:aftersale:edit')")
    @Log(title = "校园淘售后", businessType = BusinessType.UPDATE)
    @PostMapping("/audit")
    public AjaxResult audit(@Valid @RequestBody CampusAftersaleAuditDTO body) {
        // 审核人强制取自当前登录用户，忽略前端传入，避免伪造审核归属
        aftersaleService.audit(body.getAftersaleNo(), SecurityUtils.getUsername(), body.getPass(), body.getRemark());
        return AjaxResult.success("审核完成");
    }

    /** 我的售后：普通用户仅可见自己的售后单；管理员可见全部（管理端） */
    @GetMapping("/list")
    public AjaxResult list(CampusAftersale aftersale) {
        Long userId = SecurityUtils.getUserId();
        if (!SecurityUtils.isAdmin(userId)) {
            aftersale.setUserId(userId);
        }
        List<CampusAftersale> list = aftersaleService.selectList(aftersale);
        return AjaxResult.success(list);
    }
}
