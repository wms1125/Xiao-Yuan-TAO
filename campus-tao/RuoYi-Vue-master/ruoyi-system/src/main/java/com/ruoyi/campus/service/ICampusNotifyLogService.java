package com.ruoyi.campus.service;

import java.util.List;

import com.ruoyi.campus.domain.CampusNotifyLog;

/**
 * 通知外呼留痕服务（校园淘）
 */
public interface ICampusNotifyLogService {

    /**
     * 查询外呼留痕（管理端），各条件均可为空。
     *
     * @param limit 返回条数上限，越界时按服务端上限收敛
     */
    List<CampusNotifyLog> selectList(String channel, Integer status, Long userId, String bizNo, Integer limit);
}
