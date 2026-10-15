package com.ruoyi.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ruoyi.campus.service.ICampusOrderService;

/**
 * 校园淘定时任务。
 *
 * 类位于 {@code com.ruoyi.quartz.task} 包下，符合框架定时任务白名单（Constants.JOB_WHITELIST_STR），
 * 可在「系统监控 → 定时任务」界面直接维护。
 */
@Component("campusOrderTask")
public class CampusOrderTask {

    private static final Logger log = LoggerFactory.getLogger(CampusOrderTask.class);

    /** 未支付订单自动取消时限（分钟）；默认 30，可用 campus.order.pay-timeout-minutes 或环境变量 CAMPUS_PAY_TIMEOUT_MINUTES 覆盖 */
    @Value("${campus.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    @Autowired
    private ICampusOrderService orderService;

    /**
     * 运营：自动取消超时未支付的订单并回补库存（幂等）。
     * 调用于 sys_job：{@code campusOrderTask.autoCancelTimeoutOrder}，默认每分钟执行一次。
     */
    public void autoCancelTimeoutOrder() {
        // 每次重读配置，便于运行期调整时限而无需重启
        int cancelled = orderService.autoCancelTimeoutOrder(payTimeoutMinutes);
        if (cancelled > 0) {
            log.info("校园淘定时任务：已自动取消超时未支付订单 {} 笔（超时阈值 {} 分钟），库存已回补", cancelled, payTimeoutMinutes);
        }
    }
}
