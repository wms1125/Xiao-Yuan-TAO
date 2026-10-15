package com.ruoyi.campus.logistics;

import java.util.Date;

/**
 * 物流轨迹节点（Provider 返回值，未落库前的传输对象）。
 */
public class LogisticsTraceNode {

    /** 承运商编码，如 SF */
    private String carrier;
    /** 轨迹状态标签，如 已揽收 */
    private String statusLabel;
    /** 轨迹描述 */
    private String context;
    /** 轨迹发生时间 */
    private Date traceTime;

    public LogisticsTraceNode() {
    }

    public LogisticsTraceNode(String carrier, String statusLabel, String context, Date traceTime) {
        this.carrier = carrier;
        this.statusLabel = statusLabel;
        this.context = context;
        this.traceTime = traceTime;
    }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public Date getTraceTime() { return traceTime; }
    public void setTraceTime(Date traceTime) { this.traceTime = traceTime; }
}
