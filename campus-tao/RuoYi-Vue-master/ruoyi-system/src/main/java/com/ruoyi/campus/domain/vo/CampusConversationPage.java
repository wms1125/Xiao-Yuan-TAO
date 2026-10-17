package com.ruoyi.campus.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 校园淘站内信「会话列表分页」结果（校园淘）。
 *
 * <p>引入原因：会话列表原先是"一次全量返回"，用户参与过的订单越多返回体越大，
 * 且前端拿不到总数、无法分页。现在列表按 {@code pageNum/pageSize} 查询，
 * 总数单独聚合，行数与总数必须来自同一次请求——否则翻页控件会显示错的总页数。</p>
 *
 * <p>刻意不放在 {@code TableDataInfo} 里返回：Service 层不该认识 Web 层的响应体，
 * 这里只承载业务结果，由 Controller 转成表格分页结构。</p>
 */
public class CampusConversationPage {

    /** 会话总数（与 {@link #rows} 使用同一查询条件，不会漂移） */
    private long total;

    /** 当前页的会话行 */
    private List<Map<String, Object>> rows;

    public CampusConversationPage(long total, List<Map<String, Object>> rows) {
        this.total = total;
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }
}
