package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.domain.vo.CampusNoticePage;

import java.util.List;

/**
 * 站内通知服务（校园淘）
 */
public interface ICampusNoticeService {

    /** 推送通知给指定用户（下单/发货/售后审核等事件触发） */
    void push(Long userId, String type, String title, String content, String bizNo);

    /** 我的通知：isRead 为空查全部 */
    List<CampusNotice> selectListByUser(Long userId, Integer isRead, Integer limit);

    /**
     * 通知中心列表（分页 + 按 type 筛选）。
     *
     * <p>{@code pageSize} 超过 {@link #MAX_PAGE_SIZE} 直接报错，不静默截断——
     * 否则调用方以为拿到的就是全部通知。</p>
     */
    CampusNoticePage selectListByUserPaged(Long userId, Integer isRead, String type, int pageNum, int pageSize);

    /** 未读数量 */
    int countUnread(Long userId);

    /** 标记单条已读（仅本人） */
    boolean markRead(Long noticeId, Long userId);

    /** 标记本人全部已读 */
    int markReadAll(Long userId);

    /** 默认每页条数 */
    int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数硬上限（超过即报错，防止一次拉全量） */
    int MAX_PAGE_SIZE = 100;
}
