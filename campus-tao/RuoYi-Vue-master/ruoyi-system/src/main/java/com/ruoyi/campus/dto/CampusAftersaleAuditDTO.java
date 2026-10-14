package com.ruoyi.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 售后审核 DTO（校园淘）
 */
public class CampusAftersaleAuditDTO {
    @NotBlank(message = "售后单号不能为空")
    private String aftersaleNo;

    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    private String handler;

    private String remark;

    public String getAftersaleNo() {
        return aftersaleNo;
    }

    public void setAftersaleNo(String aftersaleNo) {
        this.aftersaleNo = aftersaleNo;
    }

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
