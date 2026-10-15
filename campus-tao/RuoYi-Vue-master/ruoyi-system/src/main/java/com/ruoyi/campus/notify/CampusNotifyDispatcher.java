package com.ruoyi.campus.notify;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.campus.domain.CampusNotifyLog;
import com.ruoyi.campus.mapper.CampusNotifyLogMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysUserService;

import jakarta.annotation.PreDestroy;

/**
 * 通知外呼调度器（校园淘）。
 *
 * 站内通知落库（必达）之后调用 {@link #dispatch}，由本调度器把同一条消息
 * 分发到所有已启用的外呼通道（短信/邮件），并逐通道留痕到 campus_notify_log。
 *
 * 设计要点：
 * 1) 通道未配置时留痕为「未启用（已降级）」，不阻断主流程；
 * 2) 外呼在独立线程池中异步执行，避免网关抖动拖慢下单/发货等业务事务；
 * 3) 任何异常都在此处吞掉并留痕，绝不向上抛出影响业务。
 */
@Component
public class CampusNotifyDispatcher {

    /** 留痕结果：发送失败 */
    public static final int STATUS_FAILED = 0;
    /** 留痕结果：发送成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 留痕结果：通道未启用，已降级为仅站内通知 */
    public static final int STATUS_DISABLED = 2;

    private static final Logger log = LoggerFactory.getLogger(CampusNotifyDispatcher.class);
    private static final int MAX_TITLE = 100;
    private static final int MAX_TEXT = 500;

    @Autowired
    private List<CampusNotifyChannel> channels;

    @Autowired
    private CampusNotifyLogMapper notifyLogMapper;

    @Autowired
    private ISysUserService userService;

    /** 外呼专用线程池：守护线程，容量固定，避免无界堆积 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "campus-notify-out");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 把一条站内通知分发到全部外呼通道。
     * 该方法永不抛异常，调用方可放心地在业务事务中直接调用。
     */
    public void dispatch(Long userId, String type, String title, String content, String bizNo) {
        if (userId == null || channels == null || channels.isEmpty()) {
            return;
        }
        SysUser user = null;
        try {
            user = userService.selectUserById(userId);
        } catch (Exception e) {
            log.warn("通知外呼：读取用户联系方式失败，userId={}，将按目标缺失留痕", userId, e);
        }
        for (CampusNotifyChannel channel : channels) {
            String target = channel.targetOf(user);
            if (!channel.enabled()) {
                saveLog(userId, channel.code(), target, type, title, content, bizNo,
                        STATUS_DISABLED, "", "通道未配置，已降级为仅站内通知", 0L);
                continue;
            }
            if (!channel.targetAvailable(target)) {
                saveLog(userId, channel.code(), target, type, title, content, bizNo,
                        STATUS_FAILED, "", "接收目标缺失或格式非法（" + channel.name() + "）", 0L);
                continue;
            }
            executor.submit(() -> sendAndLog(channel, target, userId, type, title, content, bizNo));
        }
    }

    private void sendAndLog(CampusNotifyChannel channel, String target, Long userId,
                            String type, String title, String content, String bizNo) {
        long start = System.currentTimeMillis();
        try {
            String response = channel.send(target, title, content);
            saveLog(userId, channel.code(), target, type, title, content, bizNo,
                    STATUS_SUCCESS, response, "", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("通知外呼失败：channel={}，userId={}，target={}", channel.code(), userId, target, e);
            saveLog(userId, channel.code(), target, type, title, content, bizNo,
                    STATUS_FAILED, "", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void saveLog(Long userId, String channel, String target, String type, String title,
                         String content, String bizNo, int status, String response, String errorMsg, long costMs) {
        try {
            CampusNotifyLog notifyLog = new CampusNotifyLog();
            notifyLog.setUserId(userId);
            notifyLog.setChannel(channel);
            notifyLog.setTarget(truncate(target, 100));
            notifyLog.setType(type);
            notifyLog.setTitle(truncate(title, MAX_TITLE));
            notifyLog.setContent(truncate(content, MAX_TEXT));
            notifyLog.setBizNo(truncate(bizNo, 32));
            notifyLog.setStatus(status);
            notifyLog.setResponse(truncate(response, MAX_TEXT));
            notifyLog.setErrorMsg(truncate(errorMsg, MAX_TEXT));
            notifyLog.setCostMs(costMs);
            notifyLogMapper.insertCampusNotifyLog(notifyLog);
        } catch (Exception e) {
            // 留痕本身失败也不能影响业务
            log.error("通知外呼：落库留痕失败，channel={}，userId={}", channel, userId, e);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
