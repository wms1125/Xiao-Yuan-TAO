package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 站内通知（校园淘，面向用户的订单/售后消息）
 */
public class CampusNotice implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long noticeId;
    private Long userId;
    /** 类型：order 订单 / ship 发货 / aftersale 售后 / system 系统 */
    private String type;
    private String title;
    private String content;
    /** 关联业务号（订单号 / 售后单号） */
    private String bizNo;
    /** 是否已读：0 未读 1 已读 */
    private Integer isRead;
    private Date createTime;
    private Date readTime;

    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getReadTime() { return readTime; }
    public void setReadTime(Date readTime) { this.readTime = readTime; }
}
