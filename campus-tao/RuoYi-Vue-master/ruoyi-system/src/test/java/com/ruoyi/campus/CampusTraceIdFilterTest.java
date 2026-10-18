package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ruoyi.campus.observability.TraceIdFilter;

import jakarta.servlet.FilterChain;

/**
 * 请求级 traceId 过滤器测试（校园淘）
 *
 * <p>覆盖四件事：无入站值时生成、合法入站值沿用、非法入站值丢弃、链路结束后清理 MDC。</p>
 *
 * <p>其中两条是负向断言，也是本类真正的价值：</p>
 * <ul>
 *   <li>含换行的入站值必须被丢弃——否则可以伪造日志条目（log forging）；</li>
 *   <li>请求结束后 MDC 必须为空——Servlet 容器复用线程，残留会把两个不相干的请求
 *       在日志里串成一条，比没有 traceId 更误导。</li>
 * </ul>
 */
@DisplayName("请求级 traceId")
class CampusTraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("无入站头时生成 32 位 traceId，写入 MDC 与响应头")
    void generatesWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/product/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(insideChain));

        String traceId = insideChain.get();
        assertNotNull(traceId, "链路内必须能读到 traceId，否则日志串不起来");
        assertEquals(32, traceId.length(), "UUID 去连字符后为 32 位");
        assertTrue(traceId.matches("[0-9a-f]{32}"), "应为小写十六进制");
        assertEquals(traceId, response.getHeader(TraceIdFilter.HEADER), "响应头须回写，便于前端报障时提供");
    }

    @Test
    @DisplayName("合法入站头沿用上游 traceId（跨服务串联）")
    void reusesValidIncoming() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/order/list");
        request.addHeader(TraceIdFilter.HEADER, "gw-7f3a9c1d4b5e");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(insideChain));

        assertEquals("gw-7f3a9c1d4b5e", insideChain.get());
        assertEquals("gw-7f3a9c1d4b5e", response.getHeader(TraceIdFilter.HEADER));
    }

    @Test
    @DisplayName("负向：含换行的入站值被丢弃并重新生成（防日志伪造）")
    void rejectsInjectedNewline() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/order/list");
        request.addHeader(TraceIdFilter.HEADER, "abc12345\n2026-10-17 INFO 伪造的日志行");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(insideChain));

        String traceId = insideChain.get();
        assertEquals(32, traceId.length());
        assertTrue(traceId.matches("[0-9a-f]{32}"), "非法入站值不得进入 MDC");
    }

    @Test
    @DisplayName("负向：超长入站值被丢弃（防日志行被撑爆）")
    void rejectsOverlongValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/order/list");
        request.addHeader(TraceIdFilter.HEADER, "a".repeat(200));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(insideChain));

        assertEquals(32, insideChain.get().length());
    }

    @Test
    @DisplayName("负向：请求结束后清空 MDC（线程复用不得串号）")
    void clearsMdcAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/order/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capture(new AtomicReference<>()));

        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "残留的 traceId 会把下一个请求的日志串到本次请求上");
    }

    @Test
    @DisplayName("负向：下游抛异常时同样清空 MDC（异常路径最容易漏）")
    void clearsMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/order/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain boom = (req, res) -> {
            throw new IllegalStateException("downstream failed");
        };

        try {
            filter.doFilter(request, response, boom);
        } catch (Exception expected) {
            // 异常原样抛出，过滤器不吞
        }

        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }

    /** 记录链路内读到的 traceId，供断言使用 */
    private FilterChain capture(AtomicReference<String> holder) {
        return (req, res) -> holder.set(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
