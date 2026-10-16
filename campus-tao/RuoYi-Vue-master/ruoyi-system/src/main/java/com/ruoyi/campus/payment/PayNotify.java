package com.ruoyi.campus.payment;

import java.math.BigDecimal;

/**
 * 渠道回调解析结果（校园淘支付）
 *
 * <p>{@code signValid} 是第一道闸门：验签不通过时调用方必须直接拒绝，
 * 否则任何人都能伪造"支付成功"回调把订单改成已支付。这是支付链路里最危险的一环，
 * 故单独成字段而非混在 paid 里。</p>
 *
 * @author campus-tao
 */
public class PayNotify {

    /** 验签是否通过。false 时其余字段均不可信 */
    private boolean signValid;
    /** 渠道回调对应的业务订单号 */
    private String orderNo;
    /** 渠道侧交易号 */
    private String channelTradeNo;
    /** 渠道是否告知"已支付成功" */
    private boolean paid;
    /** 渠道告知的金额，用于与订单金额核对（防篡改金额） */
    private BigDecimal amount;

    public PayNotify() {
    }

    public static PayNotify invalidSign() {
        PayNotify n = new PayNotify();
        n.signValid = false;
        return n;
    }

    public static PayNotify of(boolean signValid, String orderNo, String channelTradeNo, boolean paid, BigDecimal amount) {
        PayNotify n = new PayNotify();
        n.signValid = signValid;
        n.orderNo = orderNo;
        n.channelTradeNo = channelTradeNo;
        n.paid = paid;
        n.amount = amount;
        return n;
    }

    public boolean isSignValid() {
        return signValid;
    }

    public void setSignValid(boolean signValid) {
        this.signValid = signValid;
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

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
