package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 售后（校园淘）
 * 类型：1仅退款 2退货退款
 * 状态：0待审核 1通过 2拒绝 3已完成
 */
public class CampusAftersale implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String aftersaleNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String userName;
    private Integer type;
    private String reason;
    private BigDecimal refundAmount;
    private Integer status;
    private Integer orderStatusBefore;
    private String handler;
    private String handleRemark;
    private Date createTime;
    private Date handleTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAftersaleNo() { return aftersaleNo; }
    public void setAftersaleNo(String aftersaleNo) { this.aftersaleNo = aftersaleNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getOrderStatusBefore() { return orderStatusBefore; }
    public void setOrderStatusBefore(Integer orderStatusBefore) { this.orderStatusBefore = orderStatusBefore; }
    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getHandleTime() { return handleTime; }
    public void setHandleTime(Date handleTime) { this.handleTime = handleTime; }
}
