package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 通知外呼留痕（短信/邮件通道的实际发送记录）
 *
 * 用于举证"外呼是否真实发出"：每一条站内通知都会尝试走已启用的外呼通道，
 * 无论成功、失败还是通道未配置（降级），均落库留痕。
 */
public class CampusNotifyLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long logId;
    /** 接收用户ID */
    private Long userId;
    /** 渠道：sms 短信 / email 邮件 */
    private String channel;
    /** 接收目标：手机号 / 邮箱 */
    private String target;
    /** 业务类型：与站内通知一致（order/ship/aftersale/system） */
    private String type;
    private String title;
    private String content;
    /** 关联业务号（订单号/售后单号） */
    private String bizNo;
    /** 发送结果：0 失败 1 成功 2 未启用（通道未配置，已降级为仅站内通知） */
    private Integer status;
    /** 网关响应摘要（截断保存） */
    private String response;
    /** 失败原因（截断保存） */
    private String errorMsg;
    /** 耗时（毫秒） */
    private Long costMs;
    private Date createTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
