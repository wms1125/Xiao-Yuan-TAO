package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusSearchHistory;

import java.util.List;

/**
 * 搜索历史服务（校园淘）
 *
 * @author campus-tao
 */
public interface ICampusSearchHistoryService {

    /** 最近搜索（最新在前） */
    List<CampusSearchHistory> selectRecent(Long userId, int limit);

    /**
     * 记录一次搜索。
     *
     * <p>同一用户同一关键词<b>不新增、只更新时间</b>（对齐 {@code uk_user_keyword} 的语义），
     * 否则同一词会把历史列表刷满。实现为「先删后插」以让该词排到最前。</p>
     */
    void record(Long userId, String keyword);

    /** 删除一条历史（越权抛异常） */
    int remove(Long historyId, Long userId);

    /** 清空全部历史，返回删除条数 */
    int clear(Long userId);
}
