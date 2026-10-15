package com.ruoyi.campus.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.campus.domain.CampusNotifyLog;
import com.ruoyi.campus.mapper.CampusNotifyLogMapper;
import com.ruoyi.campus.service.ICampusNotifyLogService;

/**
 * 通知外呼留痕服务实现（校园淘）
 */
@Service
public class CampusNotifyLogServiceImpl implements ICampusNotifyLogService {

    /** 服务端返回条数上限，避免一次拉全表 */
    private static final int MAX_LIMIT = 500;

    @Autowired
    private CampusNotifyLogMapper notifyLogMapper;

    @Override
    public List<CampusNotifyLog> selectList(String channel, Integer status, Long userId, String bizNo, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 100 : Math.min(limit, MAX_LIMIT);
        return notifyLogMapper.selectList(channel, status, userId, bizNo, safeLimit);
    }
}
