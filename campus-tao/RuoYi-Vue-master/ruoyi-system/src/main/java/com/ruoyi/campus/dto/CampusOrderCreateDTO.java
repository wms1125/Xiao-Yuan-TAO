package com.ruoyi.campus.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 下单请求 DTO（校园淘）
 * 替代原先的 Map 接收，统一做参数合法性校验。
 */
public class CampusOrderCreateDTO {
    @NotEmpty(message = "请选择要结算的购物车商品")
    private List<Long> cartIds;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "联系电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String address;

    /**
     * 选用的收货地址ID（可选）。
     * <p>
     * 传入时服务端会**校验归属**并以地址簿内容为准覆盖
     * `receiverName` / `receiverPhone` / `address` 三个字段，同时写入订单的
     * `address_snapshot`——即"选了地址簿就不能被前端篡改"。
     * 不传则按手填内容下单（兼容原有调用方）。
     */
    private Long addressId;

    private String remark;

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public List<Long> getCartIds() {
        return cartIds;
    }

    public void setCartIds(List<Long> cartIds) {
        this.cartIds = cartIds;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
