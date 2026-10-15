package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusNotice;

import java.util.List;

/**
 * 站内通知服务（校园淘）
 */
public interface ICampusNoticeService {

    /** 推送通知给指定用户（下单/发货/售后审核等事件触发） */
    void push(Long userId, String type, String title, String content, String bizNo);

    /** 我的通知：isRead 为空查全部 */
    List<CampusNotice> selectListByUser(Long userId, Integer isRead, Integer limit);

    /** 未读数量 */
    int countUnread(Long userId);

    /** 标记单条已读（仅本人） */
    boolean markRead(Long noticeId, Long userId);

    /** 标记本人全部已读 */
    int markReadAll(Long userId);
}
