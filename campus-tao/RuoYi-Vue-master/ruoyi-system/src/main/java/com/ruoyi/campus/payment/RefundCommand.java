package com.ruoyi.campus.payment;

import java.math.BigDecimal;

/**
 * 退款指令（校园淘支付）
 *
 * @author campus-tao
 */
public class RefundCommand {

    /** 收款用户ID（退款去向） */
    private Long userId;
    /** 原业务订单号 */
    private String orderNo;
    /** 原渠道交易号；沙箱渠道可空，真实渠道退款必填 */
    private String channelTradeNo;
    /** 退款金额，必须为正数 */
    private BigDecimal amount;

    public RefundCommand() {
    }

    public RefundCommand(Long userId, String orderNo, String channelTradeNo, BigDecimal amount) {
        this.userId = userId;
        this.orderNo = orderNo;
        this.channelTradeNo = channelTradeNo;
        this.amount = amount;
    }

    public static RefundCommand of(Long userId, String orderNo, String channelTradeNo, BigDecimal amount) {
        return new RefundCommand(userId, orderNo, channelTradeNo, amount);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
