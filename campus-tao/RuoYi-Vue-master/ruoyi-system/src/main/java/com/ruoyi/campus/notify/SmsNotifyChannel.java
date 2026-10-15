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
 * 短信外呼通道。
 *
 * 网关地址由环境变量 CAMPUS_SMS_ENDPOINT 注入（application.yml 仅做占位符引用）；
 * 未注入时 {@link #enabled()} 返回 false，外呼自动降级为仅站内通知。
 * 协议为通用 HTTP 表单网关：appKey / sign / mobile / content，响应 code=0 视为成功。
 */
@Component
public class SmsNotifyChannel implements CampusNotifyChannel {

    @Value("${campus.notify.sms.endpoint:}")
    private String endpoint;

    @Value("${campus.notify.sms.app-key:}")
    private String appKey;

    @Value("${campus.notify.sms.sign:}")
    private String sign;

    @Override
    public String code() {
        return "sms";
    }

    @Override
    public String name() {
        return "短信";
    }

    @Override
    public boolean enabled() {
        return StringUtils.isNotBlank(endpoint);
    }

    @Override
    public String targetOf(SysUser user) {
        return user == null ? null : user.getPhonenumber();
    }

    @Override
    public boolean targetAvailable(String target) {
        return StringUtils.isNotBlank(target) && target.matches("\\d{6,20}");
    }

    @Override
    public String send(String target, String title, String content) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appKey", appKey);
        params.put("sign", sign);
        params.put("mobile", target);
        params.put("content", buildContent(title, content));
        String response = HttpFormClient.post(endpoint, params);
        if (!HttpFormClient.isSuccess(response)) {
            throw new IOException("短信网关返回失败：" + HttpFormClient.truncate(response, 500));
        }
        return response;
    }

    private String buildContent(String title, String content) {
        String prefix = StringUtils.isNotBlank(sign) ? "【" + sign + "】" : "";
        return prefix + StringUtils.nvl(title, "") + " " + StringUtils.nvl(content, "");
    }
}
