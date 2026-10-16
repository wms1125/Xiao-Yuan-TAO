package com.ruoyi.campus.payment;

/**
 * 退款结果（校园淘支付）
 *
 * @author campus-tao
 */
public class RefundResult {

    private boolean success;
    /** 渠道侧退款单号，成功时应有值；用于对账 */
    private String refundNo;
    /** 失败原因，仅 success=false 时有值 */
    private String failReason;

    public RefundResult() {
    }

    private RefundResult(boolean success, String refundNo, String failReason) {
        this.success = success;
        this.refundNo = refundNo;
        this.failReason = failReason;
    }

    public static RefundResult success(String refundNo) {
        return new RefundResult(true, refundNo, null);
    }

    public static RefundResult failed(String reason) {
        return new RefundResult(false, null, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
