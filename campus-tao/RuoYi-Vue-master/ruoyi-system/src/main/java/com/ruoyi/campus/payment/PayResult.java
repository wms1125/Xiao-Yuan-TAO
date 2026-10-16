package com.ruoyi.campus.payment;

/**
 * 扣款结果（校园淘支付）
 *
 * <p>把"余额不足"与"系统失败"拆成两个状态，是为了让业务层能区别处理：
 * 余额不足时订单应保持待付款并告知用户充值；系统失败则应抛异常触发事务回滚，
 * 而不是把订单静默地留在待付款状态——后者会让用户以为没付成、实际钱已扣。</p>
 *
 * @author campus-tao
 */
public class PayResult {

    public enum Status {
        /** 扣款成功 */
        SUCCESS,
        /** 余额不足（业务性拒绝，非异常） */
        INSUFFICIENT_BALANCE,
        /** 渠道侧失败（网络/渠道异常等，属系统性问题） */
        FAILED
    }

    private Status status;
    /** 渠道侧交易号，成功时应有值；用于对账与后续退款 */
    private String channelTradeNo;
    /** 失败原因，仅 FAILED 时有值 */
    private String failReason;

    public PayResult() {
    }

    private PayResult(Status status, String channelTradeNo, String failReason) {
        this.status = status;
        this.channelTradeNo = channelTradeNo;
        this.failReason = failReason;
    }

    public static PayResult success(String channelTradeNo) {
        return new PayResult(Status.SUCCESS, channelTradeNo, null);
    }

    public static PayResult insufficientBalance(String reason) {
        return new PayResult(Status.INSUFFICIENT_BALANCE, null, reason);
    }

    public static PayResult failed(String reason) {
        return new PayResult(Status.FAILED, null, reason);
    }

    public boolean isSuccess() {
        return Status.SUCCESS == status;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
