package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusSearchHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 搜索历史 Mapper（校园淘）
 *
 * @author campus-tao
 */
public interface CampusSearchHistoryMapper {

    /** 最近搜索（最新在前，最多 limit 条） */
    List<CampusSearchHistory> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);

    /** 删除某用户的某条历史（带 user_id，越权删除返回 0 行） */
    int deleteById(@Param("historyId") Long historyId, @Param("userId") Long userId);

    /**
     * 删除某用户的某关键词历史。用于"先删后插"实现同词只保留一条并置顶。
     */
    int deleteByUserAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    /** 清空某用户的全部历史 */
    int deleteAllByUser(@Param("userId") Long userId);

    /**
     * 记录一次搜索。依赖 {@code uk_user_keyword} 唯一约束：
     * 同词重复插入会抛 {@code DuplicateKeyException}，故服务层须先判断。
     */
    int insertCampusSearchHistory(CampusSearchHistory history);
}
