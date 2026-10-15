package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.campus.logistics.HttpLogisticsProvider;
import com.ruoyi.campus.logistics.LogisticsTraceNode;
import com.ruoyi.campus.support.HttpFormClient;

/**
 * 外呼/物流网关响应处理单元测试（校园淘）
 *
 * 覆盖两处纯函数逻辑，无需 Spring 容器与数据库：
 * 1) {@link HttpFormClient#isSuccess} 对网关成功约定的判定（code / success 两种风格）；
 * 2) {@link HttpLogisticsProvider#parse} 对轨迹响应体的解析与时间格式兼容。
 */
class CampusNotifyLogisticsTest {

    // ---------- 网关成功判定 ----------

    @Test
    @DisplayName("网关响应判定：code=0 视为成功")
    void 网关响应_code0_成功() {
        assertTrue(HttpFormClient.isSuccess("{\"code\":0,\"msg\":\"ok\"}"));
    }

    @Test
    @DisplayName("网关响应判定：success=true 视为成功")
    void 网关响应_successTrue_成功() {
        assertTrue(HttpFormClient.isSuccess("{\"success\":true,\"msg\":\"accepted\"}"));
    }

    @Test
    @DisplayName("网关响应判定：code 非 0 或空响应视为失败")
    void 网关响应_失败场景() {
        assertFalse(HttpFormClient.isSuccess("{\"code\":500}"));
        assertFalse(HttpFormClient.isSuccess("{\"success\":false}"));
        assertFalse(HttpFormClient.isSuccess(""));
        assertFalse(HttpFormClient.isSuccess(null));
        assertFalse(HttpFormClient.isSuccess("not-a-json"));
    }

    @Test
    @DisplayName("响应摘要截断：超长内容按上限截断")
    void 响应摘要截断() {
        assertEquals("abcdef", HttpFormClient.truncate("abcdef", 10));
        assertEquals("abc", HttpFormClient.truncate("abcdef", 3));
        assertEquals("", HttpFormClient.truncate(null, 3));
    }

    // ---------- 物流轨迹解析 ----------

    private HttpLogisticsProvider provider() {
        return new HttpLogisticsProvider();
    }

    @Test
    @DisplayName("轨迹解析：标准响应解析出节点与时间")
    void 轨迹解析_标准响应() {
        String response = "{\"success\":true,\"carrier\":\"SF\",\"traces\":["
                + "{\"time\":\"2026-10-15 09:10:00\",\"status\":\"已揽收\",\"context\":\"快件已被揽收\"},"
                + "{\"time\":\"2026-10-15 13:20:00\",\"status\":\"运输中\",\"context\":\"快件已到达中转站\"}]}";
        List<LogisticsTraceNode> nodes = provider().parse(response);
        assertEquals(2, nodes.size());
        assertEquals("已揽收", nodes.get(0).getStatusLabel());
        assertEquals("快件已被揽收", nodes.get(0).getContext());
        assertNotNull(nodes.get(0).getTraceTime());
    }

    @Test
    @DisplayName("轨迹解析：兼容 data 字段与 ISO 时间格式")
    void 轨迹解析_兼容data与ISO时间() {
        String response = "{\"code\":0,\"data\":[{\"time\":\"2026-10-15T09:10:00\",\"status\":\"已签收\",\"context\":\"已签收\"}]}";
        List<LogisticsTraceNode> nodes = provider().parse(response);
        assertEquals(1, nodes.size());
        assertEquals("已签收", nodes.get(0).getStatusLabel());
        assertNotNull(nodes.get(0).getTraceTime());
    }

    @Test
    @DisplayName("轨迹解析：空响应或状态与描述均缺失时返回空列表")
    void 轨迹解析_异常输入返回空() {
        assertTrue(provider().parse("").isEmpty());
        assertTrue(provider().parse("{\"success\":true}").isEmpty());
        assertTrue(provider().parse("{\"traces\":[{\"time\":\"bad\"}]}").isEmpty());
        assertTrue(provider().parse("not-a-json").isEmpty());
    }

    @Test
    @DisplayName("轨迹解析：时间无法解析时节点仍保留，时间为空")
    void 轨迹解析_时间无法解析仍保留节点() {
        String response = "{\"traces\":[{\"time\":\"昨天上午\",\"status\":\"派送中\",\"context\":\"快递员派送中\"}]}";
        List<LogisticsTraceNode> nodes = provider().parse(response);
        assertEquals(1, nodes.size());
        assertEquals(null, nodes.get(0).getTraceTime());
        assertEquals("派送中", nodes.get(0).getStatusLabel());
    }
}
