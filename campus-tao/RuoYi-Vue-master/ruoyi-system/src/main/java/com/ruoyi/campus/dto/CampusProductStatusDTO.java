package com.ruoyi.campus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 商品上架/下架 DTO（校园淘）
 */
public class CampusProductStatusDTO {
    @NotNull(message = "商品ID不能为空")
    private Long id;

    @NotNull(message = "商品状态不能为空")
    @Min(value = 0, message = "商品状态只能为 0(下架) 或 1(上架)")
    @Max(value = 1, message = "商品状态只能为 0(下架) 或 1(上架)")
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
