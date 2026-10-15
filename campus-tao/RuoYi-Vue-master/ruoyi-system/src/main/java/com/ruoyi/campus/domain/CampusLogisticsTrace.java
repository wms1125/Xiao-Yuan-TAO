package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 物流轨迹节点（校园淘）
 *
 * 由可配置的物流轨迹查询 Provider 拉取后落库，同一条轨迹节点按
 * （order_no, logistics_no, trace_time, status_label）去重，重复同步幂等。
 */
public class CampusLogisticsTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long traceId;
    private String orderNo;
    private String logisticsNo;
    /** 承运商编码，如 SF / YTO */
    private String carrier;
    /** 轨迹状态标签，如 已揽收 / 运输中 / 已签收 */
    private String statusLabel;
    /** 轨迹描述 */
    private String context;
    /** 轨迹发生时间 */
    private Date traceTime;
    private Date createTime;

    public Long getTraceId() { return traceId; }
    public void setTraceId(Long traceId) { this.traceId = traceId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public Date getTraceTime() { return traceTime; }
    public void setTraceTime(Date traceTime) { this.traceTime = traceTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
