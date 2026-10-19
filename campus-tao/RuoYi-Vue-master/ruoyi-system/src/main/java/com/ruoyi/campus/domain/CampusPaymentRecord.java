package com.ruoyi.campus.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 支付流水（校园淘）
 *
 * <p>支付/退款成功时落一条流水，保留渠道交易号，供对账使用。
 * 此前渠道交易号无处落库、只能丢弃，此处补上。
 */
public class CampusPaymentRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private Long sellerId;
    private BigDecimal amount;
    /** 交易类型：PAY 支付、REFUND 退款 */
    private String tradeType;
    private String channel;
    private String channelTradeNo;
    /** 结果：SUCCESS / FAIL */
    private String status;
    private String remark;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getTradeType() { return tradeType; }
    public void setTradeType(String tradeType) { this.tradeType = tradeType; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getChannelTradeNo() { return channelTradeNo; }
    public void setChannelTradeNo(String channelTradeNo) { this.channelTradeNo = channelTradeNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
