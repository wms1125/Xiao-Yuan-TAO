package com.ruoyi.campus.notify;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ruoyi.campus.support.HttpFormClient;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.StringUtils;

/**
 * 邮件外呼通道。
 *
 * 网关地址由环境变量 CAMPUS_MAIL_ENDPOINT 注入；未注入时自动降级为仅站内通知。
 * 协议为通用 HTTP 表单网关：appKey / from / to / subject / content，响应 code=0 视为成功。
 */
@Component
public class EmailNotifyChannel implements CampusNotifyChannel {

    @Value("${campus.notify.email.endpoint:}")
    private String endpoint;

    @Value("${campus.notify.email.app-key:}")
    private String appKey;

    @Value("${campus.notify.email.from:}")
    private String from;

    @Override
    public String code() {
        return "email";
    }

    @Override
    public String name() {
        return "邮件";
    }

    @Override
    public boolean enabled() {
        return StringUtils.isNotBlank(endpoint);
    }

    @Override
    public String targetOf(SysUser user) {
        return user == null ? null : user.getEmail();
    }

    @Override
    public boolean targetAvailable(String target) {
        return StringUtils.isNotBlank(target) && target.length() <= 50 && target.contains("@");
    }

    @Override
    public String send(String target, String title, String content) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appKey", appKey);
        params.put("from", from);
        params.put("to", target);
        params.put("subject", StringUtils.nvl(title, ""));
        params.put("content", StringUtils.nvl(content, ""));
        String response = HttpFormClient.post(endpoint, params);
        if (!HttpFormClient.isSuccess(response)) {
            throw new IOException("邮件网关返回失败：" + HttpFormClient.truncate(response, 500));
        }
        return response;
    }
}
