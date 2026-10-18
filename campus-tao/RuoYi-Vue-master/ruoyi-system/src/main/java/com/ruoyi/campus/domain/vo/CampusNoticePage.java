package com.ruoyi.campus.domain.vo;

import java.util.List;

/**
 * 校园淘通知中心「列表分页」结果。
 *
 * <p>引入原因：通知中心页要能翻完历史通知，而铃铛只需要 Top N。
 * 两处的语义不同，用一个 {@code List} 返回值会把"这是全部"的错觉带给调用方。
 * 这里显式带上 total，翻页控件才不会显示错的总页数。</p>
 */
public class CampusNoticePage {

    /** 当前筛选条件下的通知总数 */
    private long total;

    /** 当前页的通知行 */
    private List<com.ruoyi.campus.domain.CampusNotice> rows;

    public CampusNoticePage(long total, List<com.ruoyi.campus.domain.CampusNotice> rows) {
        this.total = total;
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<com.ruoyi.campus.domain.CampusNotice> getRows() {
        return rows;
    }

    public void setRows(List<com.ruoyi.campus.domain.CampusNotice> rows) {
        this.rows = rows;
    }
}
