package com.ruoyi.campus.exception;

import com.ruoyi.common.core.domain.AjaxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 校园淘 全局异常处理（企业级：统一异常响应，避免把堆栈抛给前端）
 * 业务异常统一使用 com.ruoyi.common.exception.ServiceException（由框架 GlobalExceptionHandler 处理，返回 code/msg）；
 * 本处理器低优先级兜底，仅捕获未预期到的 RuntimeException（如空指针等），防止堆栈泄露到前端。
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ruoyi.campus")
public class CampusGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CampusGlobalExceptionHandler.class);

    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e) {
        log.error("[校园淘未预期异常] {}", e.getMessage(), e);
        return AjaxResult.error(e.getMessage() == null ? "系统繁忙，请稍后重试" : e.getMessage());
    }
}
