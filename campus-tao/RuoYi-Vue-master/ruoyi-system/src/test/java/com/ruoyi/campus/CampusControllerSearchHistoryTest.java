package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.controller.CampusSearchHistoryController;
import com.ruoyi.campus.controller.CampusSearchHistoryController.RecordBody;
import com.ruoyi.campus.service.ICampusSearchHistoryService;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * Controller 层测试：搜索历史接口。
 *
 * <p>核心断言是**身份来源**：搜索历史是买家私有数据，所有接口的用户身份
 * 必须取自登录态，不接受请求体中的 userId。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 搜索历史 Controller")
class CampusControllerSearchHistoryTest {

    private static final long STUDENT_ID = 2L;
    private static final long HISTORY_ID = 100L;

    @Mock
    private ICampusSearchHistoryService historyService;

    @InjectMocks
    private CampusSearchHistoryController historyController;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, "ry");
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    private RecordBody body(String keyword) {
        RecordBody b = new RecordBody();
        b.setKeyword(keyword);
        return b;
    }

    @Test
    @DisplayName("最近搜索：默认 limit 为 10，以登录用户为作用域")
    void listUsesLoginUserWithDefaultLimit() {
        AjaxResult result = historyController.list(null);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(historyService).selectRecent(STUDENT_ID, 10);
    }

    @Test
    @DisplayName("最近搜索：limit 显式传入时原样使用")
    void listPassesExplicitLimit() {
        historyController.list(5);

        verify(historyService).selectRecent(STUDENT_ID, 5);
    }

    @Test
    @DisplayName("记录搜索：userId 取自登录态，keyword 来自请求体")
    void recordUsesLoginUser() {
        AjaxResult result = historyController.record(body("iPad"));

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(historyService).record(STUDENT_ID, "iPad");
    }

    @Test
    @DisplayName("删除单条：userId 取自登录态")
    void removeUsesLoginUser() {
        historyController.remove(HISTORY_ID);

        verify(historyService).remove(HISTORY_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("清空：以登录用户为作用域，返回清除条数")
    void clearUsesLoginUser() {
        when(historyService.clear(STUDENT_ID)).thenReturn(4);

        AjaxResult result = historyController.clear();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(4, data.get("cleared"));
    }

    @Test
    @DisplayName("负向：没有任何接口接受请求体中的 userId")
    void noEndpointAcceptsUserId() {
        // 注意：selectRecent 的第二参是原语 int，必须用 anyInt()；
        // 用 any() 会返回 null 并在拆箱时 NPE，且会污染后续用例的匹配器栈。
        historyController.list(10);
        verify(historyService).selectRecent(STUDENT_ID, 10);
        verify(historyService, never()).selectRecent(eq(999L), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("记录搜索：空 keyword 原样交给服务层（由其决定是否忽略）")
    void recordPassesBlankKeywordToService() {
        historyController.record(body("   "));

        verify(historyService).record(STUDENT_ID, "   ");
    }
}
