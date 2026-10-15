package com.ruoyi.campus.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.service.ICampusNoticeService;

/**
 * 站内通知 Controller（校园淘）
 *
 * 全部接口只作用于当前登录用户本人，无需按钮级权限，但不允许匿名访问。
 */
@RestController
@RequestMapping("/campus/notice")
public class CampusNoticeController extends BaseController {
    @Autowired
    private ICampusNoticeService noticeService;

    /** 我的通知列表（isRead=0 只看未读，不传查全部） */
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(required = false) Integer isRead) {
        Long userId = SecurityUtils.getUserId();
        return AjaxResult.success(noticeService.selectListByUser(userId, isRead, null));
    }

    /** 导航栏铃铛：未读Top5 + 未读总数 */
    @GetMapping("/top")
    public AjaxResult top() {
        Long userId = SecurityUtils.getUserId();
        List<CampusNotice> unread = noticeService.selectListByUser(userId, 0, 5);
        Map<String, Object> data = new HashMap<>();
        data.put("items", unread);
        data.put("unreadCount", noticeService.countUnread(userId));
        return AjaxResult.success(data);
    }

    /** 未读数量 */
    @GetMapping("/unreadCount")
    public AjaxResult unreadCount() {
        return AjaxResult.success(noticeService.countUnread(SecurityUtils.getUserId()));
    }

    /** 标记单条已读（仅本人） */
    @PostMapping("/read/{noticeId}")
    public AjaxResult read(@PathVariable Long noticeId) {
        boolean ok = noticeService.markRead(noticeId, SecurityUtils.getUserId());
        return ok ? AjaxResult.success("已读") : AjaxResult.error("通知不存在或无权操作");
    }

    /** 全部标记已读 */
    @PostMapping("/readAll")
    public AjaxResult readAll() {
        int rows = noticeService.markReadAll(SecurityUtils.getUserId());
        return AjaxResult.success("已全部标记已读", rows);
    }
}
