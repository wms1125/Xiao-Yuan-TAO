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

    int countUnread(@Param("userId") Long userId);

    /** 标记单条已读：限定 user_id，避免越权标记他人通知 */
    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId, @Param("readTime") Date readTime);

    /** 标记本人全部已读 */
    int markReadAll(@Param("userId") Long userId, @Param("readTime") Date readTime);
}
