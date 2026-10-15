package com.ruoyi.campus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.campus.service.ICampusNotifyLogService;

/**
 * 通知外呼留痕 Controller（校园淘，运营侧管理端）
 *
 * 短信/邮件均为可选外呼通道，未配置时自动降级为仅站内通知；
 * 本接口供运营核查外呼是否真实发出（成功/失败/未启用）。
 */
@RestController
@RequestMapping("/campus/notifylog")
public class CampusNotifyLogController extends BaseController {

    @Autowired
    private ICampusNotifyLogService notifyLogService;

    /** 外呼留痕列表（仅管理员） */
    @PreAuthorize("@ss.hasPermi('campus:notifylog:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(required = false) String channel,
                           @RequestParam(required = false) Integer status,
                           @RequestParam(required = false) Long userId,
                           @RequestParam(required = false) String bizNo,
                           @RequestParam(required = false) Integer limit) {
        return AjaxResult.success(notifyLogService.selectList(channel, status, userId, bizNo, limit));
    }
}
