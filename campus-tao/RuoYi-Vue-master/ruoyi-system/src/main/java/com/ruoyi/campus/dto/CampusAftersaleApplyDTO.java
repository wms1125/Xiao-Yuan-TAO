package com.ruoyi.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 售后申请 DTO（校园淘）
 */
public class CampusAftersaleApplyDTO {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "售后类型不能为空")
    private Integer type;

    @NotBlank(message = "售后原因不能为空")
    private String reason;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
