package com.ruoyi.campus.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求级 traceId 过滤器（校园淘）
 *
 * <p>把每个请求的 traceId 放进 MDC，日志 pattern 用 {@code %X{traceId}} 输出，
 * 一次请求的多条日志即可用同一个值串起来——这是"线上能排障"的最低门槛。
 * 排障时缺了这一层，就只能靠时间戳和线程名去猜，多并发下几乎不可用。</p>
 *
 * <p><b>入站值不可无条件信任</b>：上游（网关/前端/另一个服务）带来的 {@code X-Trace-Id}
 * 只在符合"长度 8~64、仅字母数字与 - _"时才沿用。直接写进 MDC 有两个后果：
 * 超长值把日志行撑爆；含换行的值可以伪造整条日志（log forging）。</p>
 *
 * <p><b>必须清理 MDC</b>：Servlet 容器复用线程，不清理会把本次 traceId 带给下一个请求，
 * 于是两个不相干的请求在日志里被串成一条——比没有 traceId 更误导。</p>
 *
 * @author campus-tao
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 与上游约定的传递头，同时也是回写头（便于前端/网关把 traceId 报给用户） */
    public static final String HEADER = "X-Trace-Id";

    /** MDC 键名，logback pattern 中的 %X{traceId} 与之对应 */
    public static final String MDC_KEY = "traceId";

    /** 可沿用的入站值：长度与字符集都收紧，超出即视为不可信 */
    private static final Pattern ACCEPTED = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = incoming != null && ACCEPTED.matcher(incoming).matches() ? incoming : newTraceId();

        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 生成 32 位无连字符 UUID，便于日志检索与按值精确匹配 */
    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
