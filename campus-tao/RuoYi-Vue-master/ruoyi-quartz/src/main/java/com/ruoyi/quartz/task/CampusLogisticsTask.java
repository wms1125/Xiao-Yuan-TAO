package com.ruoyi.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ruoyi.campus.service.ICampusLogisticsService;

/**
 * 校园淘物流轨迹定时任务。
 *
 * 类位于 {@code com.ruoyi.quartz.task} 包下，符合框架定时任务白名单（Constants.JOB_WHITELIST_STR），
 * 可在「系统监控 → 定时任务」界面直接维护。
 * 物流轨迹查询通道未配置时任务自动空转（返回 0），不产生异常。
 */
@Component("campusLogisticsTask")
public class CampusLogisticsTask {

    private static final Logger log = LoggerFactory.getLogger(CampusLogisticsTask.class);

    /** 单轮同步的订单条数上限；默认 200，可用 campus.logistics.sync-limit 或环境变量 CAMPUS_LOGISTICS_SYNC_LIMIT 覆盖 */
    @Value("${campus.logistics.sync-limit:200}")
    private int syncLimit;

    @Autowired
    private ICampusLogisticsService logisticsService;

    /**
     * 运营：为已发货且有物流单号的订单同步物流轨迹（幂等，重复节点自动忽略）。
     * 调用于 sys_job：{@code campusLogisticsTask.syncShippedTrace}，默认每 30 分钟执行一次。
     */
    public void syncShippedTrace() {
        if (!logisticsService.providerEnabled()) {
            return;
        }
        int added = logisticsService.syncShippedOrders(syncLimit);
        if (added > 0) {
            log.info("校园淘定时任务：物流轨迹同步完成，新增 {} 条节点", added);
        }
    }
}
