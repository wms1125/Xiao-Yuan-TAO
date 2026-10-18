package com.ruoyi.campus.service.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.domain.vo.CampusNoticePage;
import com.ruoyi.campus.mapper.CampusNoticeMapper;
import com.ruoyi.campus.notify.CampusNotifyDispatcher;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.common.exception.ServiceException;

import static com.ruoyi.campus.service.ICampusNoticeService.DEFAULT_PAGE_SIZE;
import static com.ruoyi.campus.service.ICampusNoticeService.MAX_PAGE_SIZE;

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
    public CampusNoticePage selectListByUserPaged(Long userId, Integer isRead, String type, int pageNum, int pageSize) {
        if (userId == null) {
            throw new ServiceException("未登录或登录态已失效");
        }
        if (type != null && type.trim().isEmpty()) {
            // 空串与 null 等价：否则 where 里会多一个永远不成立的 type = ''
            type = null;
        }
        int page = Math.max(pageNum, 1);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        if (size > MAX_PAGE_SIZE) {
            throw new ServiceException("每页最多 " + MAX_PAGE_SIZE + " 条通知");
        }

        long total = noticeMapper.countByUser(userId, isRead, type);
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            // 越界页不再打一次库：结果必为空，但仍要返回真实 total 供翻页控件跳末页
            return new CampusNoticePage(total, Collections.emptyList());
        }
        List<CampusNotice> rows = noticeMapper.selectListByUserPaged(userId, isRead, type, offset, size);
        return new CampusNoticePage(total, rows);
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
