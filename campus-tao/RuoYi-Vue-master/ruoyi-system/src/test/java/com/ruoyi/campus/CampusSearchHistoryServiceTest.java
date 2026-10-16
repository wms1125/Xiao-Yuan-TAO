package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusSearchHistory;
import com.ruoyi.campus.mapper.CampusSearchHistoryMapper;
import com.ruoyi.campus.service.impl.CampusSearchHistoryServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：搜索历史。
 *
 * <p>重点两条约束：
 * <ol>
 *   <li><b>同词去重</b>：{@code uk_user_keyword} 的语义是"同一用户同一词只保留一条"，
 *       实现必须是「先删后插」而不是直接插入——直接插入会撞唯一键抛
 *       {@code DuplicateKeyException}，且历史列表会被同一个词刷满；</li>
 *   <li><b>上限</b>：历史不能被无限增长，超出上限时淘汰最旧的一条。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 搜索历史 Service")
class CampusSearchHistoryServiceTest {

    private static final long USER_ID = 2L;
    private static final int MAX_HISTORY_PER_USER = 20;

    @Mock
    private CampusSearchHistoryMapper historyMapper;

    @InjectMocks
    private CampusSearchHistoryServiceImpl historyService;

    private CampusSearchHistory history(Long id, String keyword) {
        CampusSearchHistory h = new CampusSearchHistory();
        h.setHistoryId(id);
        h.setUserId(USER_ID);
        h.setKeyword(keyword);
        return h;
    }

    // ---------------- record ----------------

    @Test
    @DisplayName("记录搜索：新词时插入")
    void recordInsertsNewKeyword() {
        when(historyMapper.countByUserId(USER_ID)).thenReturn(0);

        historyService.record(USER_ID, "iPad");

        ArgumentCaptor<CampusSearchHistory> captor = ArgumentCaptor.forClass(CampusSearchHistory.class);
        verify(historyMapper).insertCampusSearchHistory(captor.capture());
        assertEquals("iPad", captor.getValue().getKeyword());
        assertEquals(Long.valueOf(USER_ID), captor.getValue().getUserId());
    }

    @Test
    @DisplayName("同词重复搜索：先删后插（不新增、只置顶），避免撞唯一键")
    void recordDedupesByDeleteThenInsert() {
        when(historyMapper.countByUserId(USER_ID)).thenReturn(3);

        historyService.record(USER_ID, "iPad");

        // 顺序断言：先删除旧记录
        var inOrder = org.mockito.Mockito.inOrder(historyMapper);
        inOrder.verify(historyMapper).deleteByUserAndKeyword(USER_ID, "iPad");
        inOrder.verify(historyMapper).insertCampusSearchHistory(any());
        // 不得直接插入两次
        verify(historyMapper, org.mockito.Mockito.times(1)).insertCampusSearchHistory(any());
    }

    @Test
    @DisplayName("空搜索词不记录：既无检索价值，也会污染历史列表")
    void recordSkipsBlankKeyword() {
        historyService.record(USER_ID, "   ");

        verify(historyMapper, never()).insertCampusSearchHistory(any());
        verify(historyMapper, never()).deleteByUserAndKeyword(any(), any());
    }

    @Test
    @DisplayName("负向：未登录记录被拒绝")
    void recordRejectsNullUser() {
        ServiceException ex = assertThrows(ServiceException.class, () -> historyService.record(null, "iPad"));

        assertTrue(ex.getMessage().contains("未登录"));
        verify(historyMapper, never()).insertCampusSearchHistory(any());
    }

    @Test
    @DisplayName("超长关键词被截断到表定义上限（防插入报错）")
    void recordTruncatesOverlongKeyword() {
        when(historyMapper.countByUserId(USER_ID)).thenReturn(0);
        String longKeyword = "x".repeat(80);

        historyService.record(USER_ID, longKeyword);

        ArgumentCaptor<CampusSearchHistory> captor = ArgumentCaptor.forClass(CampusSearchHistory.class);
        verify(historyMapper).insertCampusSearchHistory(captor.capture());
        assertEquals(50, captor.getValue().getKeyword().length());
    }

    @Test
    @DisplayName("超出上限时淘汰最旧的一条，防止历史无限增长")
    void recordEvictsOldestWhenFull() {
        when(historyMapper.countByUserId(USER_ID)).thenReturn(MAX_HISTORY_PER_USER);
        // 第 21 条即最旧的（列表按时间倒序，最后一个最旧）
        when(historyMapper.selectRecentByUser(USER_ID, MAX_HISTORY_PER_USER + 1))
                .thenReturn(Arrays.asList(
                        history(21L, "最新词"),
                        history(20L, "次新词"),
                        history(1L, "最旧词")));

        historyService.record(USER_ID, "新词");

        // 删掉最旧的一条（history_id=1），再插入新词
        verify(historyMapper).deleteById(1L, USER_ID);
        verify(historyMapper).insertCampusSearchHistory(any());
    }

    @Test
    @DisplayName("未达上限时不触发淘汰逻辑")
    void recordSkipsEvictionBelowLimit() {
        when(historyMapper.countByUserId(USER_ID)).thenReturn(MAX_HISTORY_PER_USER - 1);

        historyService.record(USER_ID, "iPad");

        verify(historyMapper, never()).deleteById(any(), any());
    }

    // ---------------- 查询 ----------------

    @Test
    @DisplayName("最近搜索：limit 非法时回落到默认值，防止全表拉取")
    void selectRecentClampsLimit() {
        // 0 与超大值都应收敛到默认 10
        historyService.selectRecent(USER_ID, 0);
        historyService.selectRecent(USER_ID, 9999);

        verify(historyMapper, org.mockito.Mockito.times(2)).selectRecentByUser(USER_ID, 10);
    }

    @Test
    @DisplayName("最近搜索：合法 limit 原样传入")
    void selectRecentPassesLimit() {
        historyService.selectRecent(USER_ID, 5);
        verify(historyMapper).selectRecentByUser(USER_ID, 5);
    }

    // ---------------- 删除 ----------------

    @Test
    @DisplayName("负向：删除不存在的历史（含越权）时抛异常，而不是静默返回成功")
    void removeRejectsMissing() {
        when(historyMapper.deleteById(999L, USER_ID)).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> historyService.remove(999L, USER_ID));

        assertEquals("搜索历史不存在或无权操作", ex.getMessage());
    }

    @Test
    @DisplayName("删除历史：SQL 必须带上 userId（防越权删除）")
    void removePassesUserId() {
        when(historyMapper.deleteById(100L, USER_ID)).thenReturn(1);

        historyService.remove(100L, USER_ID);

        verify(historyMapper).deleteById(100L, USER_ID);
    }

    @Test
    @DisplayName("清空历史：返回删除条数")
    void clearReturnsRowCount() {
        when(historyMapper.deleteAllByUser(USER_ID)).thenReturn(7);

        assertEquals(7, historyService.clear(USER_ID));
    }

    @Test
    @DisplayName("负向：未登录清空被拒绝")
    void clearRejectsNullUser() {
        assertThrows(ServiceException.class, () -> historyService.clear(null));
        verify(historyMapper, never()).deleteAllByUser(any());
    }

    @Test
    @DisplayName("负向：未登录查询最近搜索被拒绝")
    void selectRecentRejectsNullUser() {
        assertThrows(ServiceException.class, () -> historyService.selectRecent(null, 10));
        verify(historyMapper, never()).selectRecentByUser(any(), anyInt());
    }
}
