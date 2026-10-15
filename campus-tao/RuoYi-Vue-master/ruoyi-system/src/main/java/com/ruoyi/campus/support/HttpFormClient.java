package com.ruoyi.campus.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 外呼/物流网关共用的轻量 HTTP 表单客户端（校园淘）。
 *
 * 统一收敛三件事，避免各接入方重复实现：
 * 1) POST 表单调用，显式设置连接/读取超时，防止网关无响应时线程被长期占用；
 * 2) 网关响应是否视为成功（兼容 code=0/0000/200 与 success=true 两种约定）；
 * 3) 响应文本截断，避免超长内容落库失败。
 */
public final class HttpFormClient {

    /** 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 3000;
    /** 读取超时（毫秒） */
    private static final int READ_TIMEOUT_MS = 5000;

    private HttpFormClient() {
    }

    /**
     * 以 application/x-www-form-urlencoded 提交表单，返回响应体。
     * 非 2xx 直接抛 IOException（含状态码与响应片段），交由上层留痕或降级。
     */
    public static String post(String endpoint, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String text = read(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IOException("网关返回 HTTP " + code + "：" + truncate(text, 500));
            }
            return text;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 判断网关响应是否为成功。
     * 兼容约定：{@code success=true} 或 {@code code/ret/status} ∈ {0,200,0000,2000}。
     */
    public static boolean isSuccess(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        try {
            JSONObject obj = JSON.parseObject(response);
            if (obj == null) {
                return false;
            }
            Boolean ok = obj.getBoolean("success");
            if (ok != null) {
                return ok;
            }
            Object raw = obj.get("code");
            if (raw == null) {
                raw = obj.get("ret");
            }
            if (raw == null) {
                raw = obj.get("status");
            }
            if (raw == null) {
                return false;
            }
            String code = String.valueOf(raw).trim();
            return "0".equals(code) || "200".equals(code) || "0000".equals(code) || "2000".equals(code);
        } catch (Exception e) {
            return false;
        }
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
