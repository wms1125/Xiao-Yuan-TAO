package com.ruoyi.campus.domain;

import java.util.Date;

/**
 * 搜索历史（校园淘）
 *
 * <p>表 {@code campus_search_history} 上有 {@code uk_user_keyword(user_id, keyword)} 唯一约束，
 * 语义是"同一用户同一关键词只保留一条"——再次搜索同词时应更新时间而不是新增一行，
 * 否则历史列表会被同一个词刷满。实现上用「先删后插」保持"最近搜索排最前"。</p>
 *
 * @author campus-tao
 */
public class CampusSearchHistory {

    private Long historyId;
    private Long userId;
    private String keyword;
    private Date createTime;

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
