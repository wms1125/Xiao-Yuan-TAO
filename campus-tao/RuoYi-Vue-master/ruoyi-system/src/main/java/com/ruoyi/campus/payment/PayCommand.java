package com.ruoyi.campus.payment;

import java.math.BigDecimal;

/**
 * 扣款指令（校园淘支付）
 *
 * @author campus-tao
 */
public class PayCommand {

    /** 付款用户ID */
    private Long userId;
    /** 业务订单号（渠道侧用于关联，必填） */
    private String orderNo;
    /** 扣款金额，必须为正数 */
    private BigDecimal amount;

    public PayCommand() {
    }

    public PayCommand(Long userId, String orderNo, BigDecimal amount) {
        this.userId = userId;
        this.orderNo = orderNo;
        this.amount = amount;
    }

    public static PayCommand of(Long userId, String orderNo, BigDecimal amount) {
        return new PayCommand(userId, orderNo, amount);
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
