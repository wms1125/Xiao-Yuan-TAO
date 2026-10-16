package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusSearchHistory;
import com.ruoyi.campus.mapper.CampusSearchHistoryMapper;
import com.ruoyi.campus.service.ICampusSearchHistoryService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 搜索历史服务实现（校园淘）
 *
 * @author campus-tao
 */
@Service
public class CampusSearchHistoryServiceImpl implements ICampusSearchHistoryService {

    /** 单用户保留的历史条数上限：防止无限增长 */
    private static final int MAX_HISTORY_PER_USER = 20;

    /** 关键词长度上限（与表定义 varchar(50) 对齐） */
    private static final int MAX_KEYWORD_LENGTH = 50;

    /** 前端默认拉取的条数 */
    private static final int DEFAULT_LIMIT = 10;

    @Autowired
    private CampusSearchHistoryMapper historyMapper;

    @Override
    public List<CampusSearchHistory> selectRecent(Long userId, int limit) {
        requireUser(userId);
        int safeLimit = limit <= 0 || limit > MAX_HISTORY_PER_USER ? DEFAULT_LIMIT : limit;
        return historyMapper.selectRecentByUser(userId, safeLimit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long userId, String keyword) {
        requireUser(userId);
        if (keyword == null || keyword.trim().isEmpty()) {
            return; // 空搜索词不记录：既没检索价值，也会污染历史列表
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            trimmed = trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        // 先删后插：同词不新增、只置顶，与 uk_user_keyword 的语义对齐
        historyMapper.deleteByUserAndKeyword(userId, trimmed);

        int existing = historyMapper.countByUserId(userId);
        if (existing >= MAX_HISTORY_PER_USER) {
            // 超出上限时丢弃最旧的一条，再插入新的，避免无上限增长
            List<CampusSearchHistory> all = historyMapper.selectRecentByUser(userId, MAX_HISTORY_PER_USER + 1);
            if (!all.isEmpty()) {
                historyMapper.deleteById(all.get(all.size() - 1).getHistoryId(), userId);
            }
        }

        CampusSearchHistory history = new CampusSearchHistory();
        history.setUserId(userId);
        history.setKeyword(trimmed);
        historyMapper.insertCampusSearchHistory(history);
    }

    @Override
    public int remove(Long historyId, Long userId) {
        requireUser(userId);
        int rows = historyMapper.deleteById(historyId, userId);
        if (rows == 0) {
            throw new ServiceException("搜索历史不存在或无权操作");
        }
        return rows;
    }

    @Override
    public int clear(Long userId) {
        requireUser(userId);
        return historyMapper.deleteAllByUser(userId);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ServiceException("未登录或登录态已失效");
        }
    }
}
