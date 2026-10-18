package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户收款码（C2C 直付）。
 *
 * <p>C2C 直付模式下买家扫码把货款直接付给卖家本人，平台不经手资金；
 * 本表只存收款码图片的引用，不含任何资金信息。
 */
public class CampusUserPayQr implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 卖家用户 ID（主键，一人一码） */
    private Long userId;
    /** 收款码图片 URL（/profile/upload/...） */
    private String qrUrl;
    private Date createTime;
    private Date updateTime;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQrUrl() { return qrUrl; }
    public void setQrUrl(String qrUrl) { this.qrUrl = qrUrl; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
