package com.ruoyi.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 收货地址新增/修改请求 DTO（校园淘）
 * <p>
 * 新增与修改字段一致，故共用一个 DTO；修改时 `addressId` 必填（在 Controller 层区分）。
 */
public class CampusAddressSaveDTO {
    private Long addressId;

    @NotBlank(message = "收货人不能为空")
    @Size(max = 32, message = "收货人长度不能超过 32 个字符")
    private String receiver;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String phone;

    @Size(max = 100, message = "所在地区长度不能超过 100 个字符")
    private String region;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址长度不能超过 200 个字符")
    private String detail;

    /** 是否设为默认地址：1 是 / 0 否，缺省视为否 */
    private Integer isDefault;

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
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
}
