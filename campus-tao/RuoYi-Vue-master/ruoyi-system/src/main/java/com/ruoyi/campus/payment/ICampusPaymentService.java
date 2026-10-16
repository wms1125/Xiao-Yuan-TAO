package com.ruoyi.campus.payment;

/**
 * 支付业务门面（校园淘）
 *
 * <p>业务代码（订单、售后）只依赖本接口，不直接持有任何具体渠道——
 * 这是把"支付方式"从业务逻辑里彻底摘掉的那道缝。</p>
 *
 * @author campus-tao
 */
public interface ICampusPaymentService {

    /**
     * 发起扣款。渠道路由失败（渠道未配置/未启用）会抛异常。
     *
     * @return 渠道结果；余额不足时业务层应保持订单待付款并提示用户充值
     */
    PayResult pay(PayCommand cmd);

    /**
     * 发起退款。
     */
    RefundResult refund(RefundCommand cmd);

    /**
     * 处理渠道"支付成功"回调。
     *
     * <p><b>必须幂等</b>：渠道会重试推送，重复推送不得重复改状态。
     * 另外此处<b>不扣站内余额</b>——真实渠道的钱由第三方收取，
     * 回调只负责把订单状态推进到已支付。</p>
     *
     * @return true 表示本次回调真正推进了状态；false 表示重复回调（已处理过）
     */
    boolean handlePaidNotify(String channel, PayNotify notify);

    /** 当前生效渠道名，用于对账与运维排查 */
    String currentChannel();
}
