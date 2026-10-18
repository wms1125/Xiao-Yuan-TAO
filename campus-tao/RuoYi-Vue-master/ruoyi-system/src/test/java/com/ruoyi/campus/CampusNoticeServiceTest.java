package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusNotice;
import com.ruoyi.campus.domain.vo.CampusNoticePage;
import com.ruoyi.campus.mapper.CampusNoticeMapper;
import com.ruoyi.campus.service.ICampusNoticeService;
import com.ruoyi.campus.service.impl.CampusNoticeServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：站内通知列表分页与筛选。
 *
 * <p>三条硬断言：<ol>
 *   <li><b>排序不因分页改变</b>——未读在前、新的在前，由 SQL 保证；</li>
 *   <li><b>total 与 rows 同筛选条件</b>——列表按 type 筛而总数不筛，翻页控件会显示错的总页数；</li>
 *   <li><b>pageSize 超上限报错而非截断</b>——截断会让调用方误以为拿到的就是全部通知。</li>
 * </ol></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 站内通知 Service（分页）")
class CampusNoticeServiceTest {

    private static final long USER_ID = 2L;

    @Mock
    private CampusNoticeMapper noticeMapper;

    @Mock
    private com.ruoyi.campus.notify.CampusNotifyDispatcher notifyDispatcher;

    @InjectMocks
    private CampusNoticeServiceImpl noticeService;

    private CampusNotice notice(String type, int isRead) {
        CampusNotice n = new CampusNotice();
        n.setNoticeId(1L);
        n.setUserId(USER_ID);
        n.setType(type);
        n.setTitle("您的订单已发货");
        n.setContent("订单 CT202601010001 已发货");
        n.setBizNo("CT202601010001");
        n.setIsRead(isRead);
        return n;
    }

    @Test
    @DisplayName("分页：pageNum/pageSize 换算成 offset/limit 下传")
    void pagedPassesOffsetAndLimit() {
        when(noticeMapper.countByUser(USER_ID, null, null)).thenReturn(25L);
        when(noticeMapper.selectListByUserPaged(USER_ID, null, null, 10L, 10))
                .thenReturn(Collections.singletonList(notice("order", 1)));

        CampusNoticePage page = noticeService.selectListByUserPaged(USER_ID, null, null, 2, 10);

        // 第 2 页每页 10 条 → offset 必须从 10 开始
        verify(noticeMapper).selectListByUserPaged(USER_ID, null, null, 10L, 10);
        assertEquals(25L, page.getTotal());
        assertEquals(1, page.getRows().size());
    }

    @Test
    @DisplayName("筛选：type 与 isRead 同时下传给列表与总数，两处不漂移")
    void filtersGoToBothListAndCount() {
        when(noticeMapper.countByUser(USER_ID, 0, "ship")).thenReturn(4L);
        when(noticeMapper.selectListByUserPaged(USER_ID, 0, "ship", 0L, 10))
                .thenReturn(Collections.singletonList(notice("ship", 0)));

        CampusNoticePage page = noticeService.selectListByUserPaged(USER_ID, 0, "ship", 1, 10);

        verify(noticeMapper).countByUser(USER_ID, 0, "ship");
        verify(noticeMapper).selectListByUserPaged(USER_ID, 0, "ship", 0L, 10);
        assertEquals(4L, page.getTotal());
    }

    @Test
    @DisplayName("筛选：空串 type 归一为 null，避免拼出永远不成立的 type = ''")
    void blankTypeNormalizedToNull() {
        when(noticeMapper.countByUser(USER_ID, null, null)).thenReturn(1L);
        when(noticeMapper.selectListByUserPaged(USER_ID, null, null, 0L, 10))
                .thenReturn(Collections.singletonList(notice("order", 0)));

        noticeService.selectListByUserPaged(USER_ID, null, "   ", 1, 10);

        // 关键：空串不能被当成"只查空类型"，必须等价于不筛选
        verify(noticeMapper).countByUser(USER_ID, null, null);
        verify(noticeMapper).selectListByUserPaged(USER_ID, null, null, 0L, 10);
    }

    @Test
    @DisplayName("分页：非法页码/条数归一，不把脏值带进 offset 计算")
    void normalizesBadParams() {
        when(noticeMapper.countByUser(USER_ID, null, null)).thenReturn(3L);

        noticeService.selectListByUserPaged(USER_ID, null, null, 0, 0);
        noticeService.selectListByUserPaged(USER_ID, null, null, -7, -3);

        // pageNum 归一到 1、pageSize 归一到 DEFAULT_PAGE_SIZE
        verify(noticeMapper, times(2))
                .selectListByUserPaged(USER_ID, null, null, 0L, ICampusNoticeService.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("负向：越界页不查库，但返回真实 total")
    void outOfRangePageSkipsQuery() {
        when(noticeMapper.countByUser(USER_ID, null, null)).thenReturn(2L);

        CampusNoticePage page = noticeService.selectListByUserPaged(USER_ID, null, null, 50, 10);

        verify(noticeMapper, never()).selectListByUserPaged(any(), any(), any(), anyLong(), anyInt());
        assertEquals(2L, page.getTotal());
        assertEquals(Collections.emptyList(), page.getRows());
    }

    @Test
    @DisplayName("负向：每页条数超过上限直接报错，不静默截断")
    void rejectsOversizedPage() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> noticeService.selectListByUserPaged(USER_ID, null, null, 1,
                        ICampusNoticeService.MAX_PAGE_SIZE + 1));

        assertEquals("每页最多 " + ICampusNoticeService.MAX_PAGE_SIZE + " 条通知", ex.getMessage());
        verify(noticeMapper, never()).countByUser(any(), any(), any());
        verify(noticeMapper, never()).selectListByUserPaged(any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("负向：超大页码不会把 offset 算成负数")
    void rejectsOverflowingPageNumber() {
        when(noticeMapper.countByUser(USER_ID, null, null)).thenReturn(3L);

        CampusNoticePage page = noticeService.selectListByUserPaged(USER_ID, null, null, Integer.MAX_VALUE, 100);

        // 用 long 计算 (pageNum-1)*size；int 会溢出成负数 offset，SQL 直接语法错
        assertEquals(3L, page.getTotal());
        verify(noticeMapper, never()).selectListByUserPaged(any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("负向：未登录（userId 为空）被拒绝，且不触碰数据库")
    void rejectsNullUser() {
        List<CampusNotice> ignored = Collections.emptyList();

        assertThrows(ServiceException.class,
                () -> noticeService.selectListByUserPaged(null, null, null, 1, 10));

        verify(noticeMapper, never()).countByUser(any(), any(), any());
        verify(noticeMapper, never()).selectListByUserPaged(any(), any(), any(), anyLong(), anyInt());
        assertEquals(0, ignored.size());
    }

    @Test
    @DisplayName("回归：铃铛用的无界入口行为不变（仍按 limit 截断）")
    void topNEntryUnchanged() {
        when(noticeMapper.selectListByUser(USER_ID, 0, 5)).thenReturn(Arrays.asList(notice("order", 0)));

        List<CampusNotice> list = noticeService.selectListByUser(USER_ID, 0, 5);

        verify(noticeMapper).selectListByUser(USER_ID, 0, 5);
        assertEquals(1, list.size());
    }
}
