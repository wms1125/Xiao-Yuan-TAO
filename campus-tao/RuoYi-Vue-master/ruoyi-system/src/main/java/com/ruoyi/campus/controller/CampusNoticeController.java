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

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.domain.vo.CampusNoticePage;
import com.ruoyi.campus.security.SelfServiceEndpoint;
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

    /**
     * 通知中心列表（分页 + 按类型/已读状态筛选）。
     *
     * <p>区别于 {@link #list}：{@link #list} 是铃铛等场景用的"取若干条"，
     * 这里要能翻完历史，故返回 {@link TableDataInfo} 并带 total。
     * 排序仍是"未读在前、新的在前"，由 SQL 保证，不因翻页改变。</p>
     *
     * @param pageNum 页码，从 1 开始；小于 1 归一为 1
     * @param pageSize 每页条数，超过 {@link ICampusNoticeService#MAX_PAGE_SIZE} 直接报错
     */
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(required = false) Integer isRead,
                              @RequestParam(required = false) String type,
                              @RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize) {
        CampusNoticePage result = noticeService.selectListByUserPaged(
                SecurityUtils.getUserId(), isRead, type, pageNum, pageSize);
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setTotal(result.getTotal());
        rspData.setRows(result.getRows());
        return rspData;
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
    @SelfServiceEndpoint("只改自己的通知：SQL 条件带 user_id，且 userId 取自登录态")
    @PostMapping("/read/{noticeId}")
    public AjaxResult read(@PathVariable Long noticeId) {
        boolean ok = noticeService.markRead(noticeId, SecurityUtils.getUserId());
        return ok ? AjaxResult.success("已读") : AjaxResult.error("通知不存在或无权操作");
    }

    /** 全部标记已读 */
    @SelfServiceEndpoint("只改自己的通知：SQL 条件带 user_id，且 userId 取自登录态")
    @PostMapping("/readAll")
    public AjaxResult readAll() {
        int rows = noticeService.markReadAll(SecurityUtils.getUserId());
        return AjaxResult.success("已全部标记已读", rows);
    }
}
