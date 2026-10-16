package com.ruoyi.campus.domain;

import java.util.Date;

/**
 * 站内信（校园淘）
 *
 * <p>会话<b>以订单为上下文</b>（表 {@code campus_message} 的 {@code order_no}），
 * 而非通用的 IM 会话。这样设计的理由：校园淘是 C2C 交易，买卖双方的沟通几乎都围绕
 * 某一笔具体订单（问成色、改地址、催发货），脱离订单的"随便找人聊"既没有业务场景，
 * 也会带来骚扰风险。</p>
 *
 * <p><b>接收方由服务端从订单推导并校验</b>，不接受前端直接传 {@code toUserId}——
 * 否则把用户 ID 作为入参即可给任意用户发消息，这是典型的越权写。</p>
 *
 * @author campus-tao
 */
public class CampusMessage {

    private Long messageId;
    private String orderNo;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    /** 0 未读 1 已读 */
    private Integer isRead;
    private Date createTime;

    // ---- 联表冗余：会话列表填充对方昵称，避免前端再查用户 ----
    private String fromUserName;
    private String toUserName;

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getIsRead() {
        return isRead;
    }

    public void setIsRead(Integer isRead) {
        this.isRead = isRead;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    public String getToUserName() {
        return toUserName;
    }

    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }
}
