package com.ruoyi.campus.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.mapper.CampusNoticeMapper;
import com.ruoyi.campus.notify.CampusNotifyDispatcher;
import com.ruoyi.campus.service.ICampusNoticeService;

/**
 * 站内通知服务实现（校园淘）
 */
@Service
public class CampusNoticeServiceImpl implements ICampusNoticeService {

    /** 与 campus_notice 表列宽一致，超长在入口处截断，避免落库报错 */
    private static final int MAX_TITLE = 100;
    private static final int MAX_CONTENT = 500;

    @Autowired
    private CampusNoticeMapper noticeMapper;

    @Autowired
    private CampusNotifyDispatcher notifyDispatcher;

    @Override
    public void push(Long userId, String type, String title, String content, String bizNo) {
        if (userId == null) {
            return;
        }
        CampusNotice notice = new CampusNotice();
        notice.setUserId(userId);
        notice.setType(type);
        notice.setTitle(truncate(title, MAX_TITLE));
        notice.setContent(truncate(content, MAX_CONTENT));
        notice.setBizNo(bizNo);
        noticeMapper.insertCampusNotice(notice);
        // 站内通知为必达链路；短信/邮件为可选外呼链路，未配置时自动降级，异常不影响主流程
        notifyDispatcher.dispatch(userId, type, notice.getTitle(), notice.getContent(), bizNo);
    }

    @Override
    public List<CampusNotice> selectListByUser(Long userId, Integer isRead, Integer limit) {
        return noticeMapper.selectListByUser(userId, isRead, limit);
    }

    @Override
    public int countUnread(Long userId) {
        return noticeMapper.countUnread(userId);
    }

    @Override
    public boolean markRead(Long noticeId, Long userId) {
        if (noticeId == null || userId == null) {
            return false;
        }
        return noticeMapper.markRead(noticeId, userId, new Date()) > 0;
    }

    @Override
    public int markReadAll(Long userId) {
        return noticeMapper.markReadAll(userId, new Date());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
