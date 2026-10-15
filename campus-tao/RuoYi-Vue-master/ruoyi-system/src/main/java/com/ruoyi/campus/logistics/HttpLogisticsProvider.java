package com.ruoyi.campus.logistics;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.campus.support.HttpFormClient;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 通用 HTTP 表单网关实现的物流轨迹 Provider。
 *
 * 网关地址与密钥由环境变量注入（CAMPUS_LOGISTICS_ENDPOINT / CAMPUS_LOGISTICS_APP_KEY），
 * 未注入时自动降级为「未启用」，轨迹查询返回空且不报错。
 *
 * 约定响应体（兼容 traces 与 data 两种字段名）：
 * <pre>
 * {"success":true,"carrier":"SF","traces":[
 *     {"time":"2026-10-15 09:10:00","status":"已揽收","context":"快件已被揽收"}
 * ]}
 * </pre>
 */
@Component
public class HttpLogisticsProvider implements ILogisticsProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpLogisticsProvider.class);

    private static final String[] TIME_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm"
    };

    /** 与 campus_logistics_trace.context 列宽一致 */
    private static final int MAX_CONTEXT = 255;

    @Value("${campus.logistics.endpoint:}")
    private String endpoint;

    @Value("${campus.logistics.app-key:}")
    private String appKey;

    @Value("${campus.logistics.carrier:SF}")
    private String carrier;

    @Override
    public boolean enabled() {
        return StringUtils.isNotBlank(endpoint);
    }

    @Override
    public String carrier() {
        return StringUtils.isNotBlank(carrier) ? carrier : "UNKNOWN";
    }

    @Override
    public List<LogisticsTraceNode> query(String logisticsNo) {
        if (!enabled() || StringUtils.isBlank(logisticsNo)) {
            return new ArrayList<>();
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appKey", appKey);
        params.put("carrier", carrier());
        params.put("logisticsNo", logisticsNo);
        String response;
        try {
            response = HttpFormClient.post(endpoint, params);
        } catch (IOException e) {
            throw new ServiceException("物流轨迹查询网关调用失败：" + e.getMessage());
        }
        if (!HttpFormClient.isSuccess(response)) {
            throw new ServiceException("物流网关返回失败：" + HttpFormClient.truncate(response, 500));
        }
        return parse(response);
    }

    /** 解析网关响应为轨迹节点列表；时间无法解析时节点仍保留，trace_time 记为空 */
    public List<LogisticsTraceNode> parse(String response) {
        List<LogisticsTraceNode> nodes = new ArrayList<>();
        try {
            JSONObject obj = JSON.parseObject(response);
            if (obj == null) {
                return nodes;
            }
            JSONArray array = obj.getJSONArray("traces");
            if (array == null) {
                array = obj.getJSONArray("data");
            }
            if (array == null) {
                return nodes;
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String status = item.getString("status");
                String context = item.getString("context");
                if (StringUtils.isBlank(context) && StringUtils.isBlank(status)) {
                    continue;
                }
                nodes.add(new LogisticsTraceNode(carrier(), StringUtils.nvl(status, ""),
                        HttpFormClient.truncate(context, MAX_CONTEXT), parseTime(item.getString("time"))));
            }
        } catch (Exception e) {
            log.warn("物流轨迹响应解析失败，将返回空轨迹：{}", HttpFormClient.truncate(response, 200), e);
        }
        return nodes;
    }

    private java.util.Date parseTime(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        for (String pattern : TIME_PATTERNS) {
            try {
                return new SimpleDateFormat(pattern).parse(value);
            } catch (ParseException ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }
}
