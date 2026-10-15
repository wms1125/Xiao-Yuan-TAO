package com.ruoyi.campus.notify;

import com.ruoyi.common.core.domain.entity.SysUser;

/**
 * 通知外呼通道抽象（校园淘）
 *
 * 站内通知（campus_notice）是必达链路；外呼通道（短信/邮件）为可选增强链路。
 * 通道未配置时 {@link #enabled()} 返回 false，业务自动降级为仅站内通知，不影响主流程。
 */
public interface CampusNotifyChannel {

    /** 渠道编码：sms / email */
    String code();

    /** 渠道中文名（用于留痕与提示文案） */
    String name();

    /** 是否已配置启用：未配置（如网关地址为空）时返回 false */
    boolean enabled();

    /** 解析接收目标：短信取手机号，邮件取邮箱 */
    String targetOf(SysUser user);

    /** 目标是否合法可用：为空或格式非法时返回 false，不发起调用 */
    boolean targetAvailable(String target);

    /**
     * 发起外呼并返回网关响应摘要。
     * 失败时抛异常（由调度器捕获并留痕），成功时返回原始响应体。
     */
    String send(String target, String title, String content) throws Exception;
}
