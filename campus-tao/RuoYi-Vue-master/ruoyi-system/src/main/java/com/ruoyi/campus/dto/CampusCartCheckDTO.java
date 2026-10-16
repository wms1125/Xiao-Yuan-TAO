package com.ruoyi.campus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 购物车勾选切换 DTO（校园淘）
 */
public class CampusCartCheckDTO {
    @NotNull(message = "购物车项ID不能为空")
    private Long id;

    @NotNull(message = "勾选状态不能为空")
    @Min(value = 0, message = "勾选状态只能为 0 或 1")
    @Max(value = 1, message = "勾选状态只能为 0 或 1")
    private Integer checked;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getChecked() {
        return checked;
    }

    public void setChecked(Integer checked) {
        this.checked = checked;
    }
}
