package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import com.ruoyi.framework.security.handle.AuthenticationEntryPointImpl;

/**
 * 认证入口点的 HTTP 语义测试。
 *
 * <p>历史缺陷：认证失败返回 HTTP 200 + body code=401（renderString 把状态码写死 200）。
 * 该问题单测看不见——所有 Controller 层测试都不经过 Security 过滤器链；
 * e2e 的断言也写成 {@code st == 401 or code == 401} 两可，等于默认了坏行为。</p>
 *
 * <p>本测试用真实 Servlet 对象（MockHttpServletRequest/Response）直接驱动入口点，
 * 断言<b>终态</b>：HTTP 状态码必须是 401，body 仍是结构化的 AjaxResult。
 * 用 mock 验"调用过 setStatus"是自欺——终态才等价于线上行为。</p>
 */
@DisplayName("认证入口点：未认证必须返回 HTTP 401")
class AuthenticationEntryPointStatusTest {

    @Test
    @DisplayName("未认证请求 → HTTP 401 + 结构化 body（code=401），而非 200")
    void commenceMustSetRealHttpStatus() throws Exception {
        AuthenticationEntryPointImpl entryPoint = new AuthenticationEntryPointImpl();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/campus/notice/top");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("not logged in"));

        assertEquals(401, response.getStatus(), "认证失败的 HTTP 状态码必须是 401");
        // Mock 响应会把 charset 拼进 contentType（application/json;charset=utf-8），只断言主类型
        assertTrue(response.getContentType().startsWith("application/json"), "实际：" + response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":401"), "body 仍需保留结构化 code=401，实际：" + body);
        assertTrue(body.contains("/campus/notice/top"), "body 需指明被拒的 URI，实际：" + body);
    }
}
