package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusNotifyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知外呼留痕 Mapper（校园淘）
 */
@Mapper
public interface CampusNotifyLogMapper {

    int insertCampusNotifyLog(CampusNotifyLog notifyLog);

    /** 外呼留痕检索（管理端）：渠道/结果/用户/业务号均可为空 */
    List<CampusNotifyLog> selectList(@Param("channel") String channel, @Param("status") Integer status,
                                     @Param("userId") Long userId, @Param("bizNo") String bizNo,
                                     @Param("limit") Integer limit);
}
