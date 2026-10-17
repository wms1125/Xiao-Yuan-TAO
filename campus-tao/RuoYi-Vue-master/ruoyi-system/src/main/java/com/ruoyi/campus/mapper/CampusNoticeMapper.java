package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 站内通知 Mapper（校园淘）
 */
@Mapper
public interface CampusNoticeMapper {
    int insertCampusNotice(CampusNotice notice);

    CampusNotice selectByNoticeId(@Param("noticeId") Long noticeId);

    List<CampusNotice> selectListByUser(@Param("userId") Long userId, @Param("isRead") Integer isRead,
                                        @Param("limit") Integer limit);

    /**
     * 我的通知（分页 + 按 type 筛选）。
     *
     * <p>铃铛用的 {@link #selectListByUser} 只取 Top N，保留无界语义；
     * 通知中心页要能翻完所有历史，故单独提供分页入口。</p>
     */
    List<CampusNotice> selectListByUserPaged(@Param("userId") Long userId, @Param("isRead") Integer isRead,
                                             @Param("type") String type, @Param("offset") long offset,
                                             @Param("limit") int limit);

    /** 与 {@link #selectListByUserPaged} 同一筛选条件的总数 */
    long countByUser(@Param("userId") Long userId, @Param("isRead") Integer isRead, @Param("type") String type);

    int countUnread(@Param("userId") Long userId);

    /** 标记单条已读：限定 user_id，避免越权标记他人通知 */
    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId, @Param("readTime") Date readTime);

    /** 标记本人全部已读 */
    int markReadAll(@Param("userId") Long userId, @Param("readTime") Date readTime);
}
