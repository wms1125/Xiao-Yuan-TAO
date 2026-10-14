package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 支付沙箱账户（校园淘）
 */
public class CampusAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private java.math.BigDecimal balance;
    private java.math.BigDecimal freeze;
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public java.math.BigDecimal getBalance() { return balance; }
    public void setBalance(java.math.BigDecimal balance) { this.balance = balance; }
    public java.math.BigDecimal getFreeze() { return freeze; }
    public void setFreeze(java.math.BigDecimal freeze) { this.freeze = freeze; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
