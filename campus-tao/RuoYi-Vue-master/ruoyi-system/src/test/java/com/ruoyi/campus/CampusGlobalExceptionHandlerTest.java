package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.campus.exception.CampusGlobalExceptionHandler;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 兜底异常处理测试：未预期异常不得把堆栈抛给前端。
 */
@DisplayName("校园淘 全局异常兜底处理")
class CampusGlobalExceptionHandlerTest {

    private final CampusGlobalExceptionHandler handler = new CampusGlobalExceptionHandler();

    @Test
    @DisplayName("带消息的运行时异常：原样返回消息体，code=500")
    void returnsMessageForRuntimeException() {
        AjaxResult result = handler.handleRuntimeException(new IllegalStateException("库存不足"));

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("库存不足", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    @DisplayName("无消息的运行时异常：回退为通用文案，不泄露内部细节")
    void fallsBackToGenericMessageWhenMessageAbsent() {
        AjaxResult result = handler.handleRuntimeException(new NullPointerException());

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("系统繁忙，请稍后重试", result.get(AjaxResult.MSG_TAG));
    }
}
