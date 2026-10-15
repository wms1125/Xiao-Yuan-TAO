package com.ruoyi.campus.domain.vo;

import java.math.BigDecimal;

/**
 * 校园淘列表页「概览」聚合行：按业务状态分组计数（售后单额外带退款金额合计）。
 *
 * <p>引入原因：列表改为分页后，前端不能再靠遍历当前页推导概览数字，
 * 概览必须由服务端按同一筛选条件聚合返回，否则「待付款/待收货」等数字会退化为"本页数量"。
 */
public class CampusStatusStat {

    /** 业务状态值 */
    private Integer status;

    /** 该状态下的记录数 */
    private Long count;

    /** 该状态下的金额合计（仅售后单使用，可为 null） */
    private BigDecimal amount;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
