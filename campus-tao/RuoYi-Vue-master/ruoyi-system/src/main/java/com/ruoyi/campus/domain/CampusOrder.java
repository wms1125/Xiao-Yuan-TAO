package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 订单（校园淘）
 * 状态：0待付款 1已付款 2已发货 3已完成 4已取消 5退款中 6已退款
 */
public class CampusOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private String userName;
    private java.math.BigDecimal totalAmount;
    private Integer status;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String remark;
    private Date payTime;
    private Date shipTime;
    /** 物流单号（发货时录入） */
    private String logisticsNo;
    private Date finishTime;
    private Date cancelTime;
    private Date createTime;
    private Date updateTime;

    /** 订单项（非表字段，查询时填充） */
    private List<CampusOrderItem> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(java.math.BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }
    public Date getShipTime() { return shipTime; }
    public void setShipTime(Date shipTime) { this.shipTime = shipTime; }
    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }
    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }
    public Date getCancelTime() { return cancelTime; }
    public void setCancelTime(Date cancelTime) { this.cancelTime = cancelTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public List<CampusOrderItem> getItems() { return items; }
    public void setItems(List<CampusOrderItem> items) { this.items = items; }
}
