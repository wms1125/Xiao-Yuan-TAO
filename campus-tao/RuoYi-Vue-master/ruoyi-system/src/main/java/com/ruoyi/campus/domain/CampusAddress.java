package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 收货地址（校园淘）
 * <p>
 * 买家自维护的收货地址簿。下单时可选择其中一条，服务层会把地址内容
 * **快照**写入订单（`campus_order.address_snapshot`），避免地址后续被修改
 * 导致历史订单信息漂移。
 */
public class CampusAddress implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long addressId;
    private Long userId;
    private String receiver;
    private String phone;
    private String region;
    private String detail;
    private Integer isDefault;
    private Date createTime;
    private Date updateTime;

    /** 完整地址（地区 + 详细），用于订单快照与列表展示 */
    public String fullAddress() {
        return (region == null ? "" : region) + (detail == null ? "" : detail);
    }

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
