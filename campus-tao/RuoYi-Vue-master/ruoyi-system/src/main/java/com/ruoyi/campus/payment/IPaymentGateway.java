package com.ruoyi.campus.payment;

import java.util.Map;

/**
 * 支付渠道统一抽象（校园淘）
 *
 * <p>为什么要这一层：改造前订单支付与退款直接依赖 {@code ICampusAccountService}（沙箱内部余额），
 * 等于把"支付方式"焊死在了业务代码里。抽掉这层耦合后：</p>
 * <ul>
 *   <li>现有沙箱行为由一个实现承载，业务代码只面向接口；</li>
 *   <li>接真实渠道（微信/支付宝）只需新增实现类 + 改一行配置，不动订单与售后逻辑；</li>
 *   <li>回调验签这类渠道相关细节被关在实现内部，不会泄漏到业务层。</li>
 * </ul>
 *
 * <p><b>语义约定（重要）</b>：{@link #pay} 表示"向渠道发起扣款"。
 * 沙箱渠道扣的是站内余额；真实渠道扣的是用户在第三方账户里的钱，
 * 到账由异步回调确认（见 {@link #parseNotify}）。
 * 因此<b>不允许</b>在实现里同时做"扣余额"和"改订单状态"——状态流转归业务层。</p>
 *
 * @author campus-tao
 */
public interface IPaymentGateway {

    /** 渠道标识，与配置项 campus.payment.channel 的取值一一对应，如 sandbox / wechat */
    String channel();

    /** 渠道是否可用。false 时渠道路由会拒绝解析，避免误把流量打到未配置的渠道上 */
    boolean enabled();

    /**
     * 发起扣款。
     *
     * @param cmd 扣款指令（用户、订单号、金额）
     * @return 结果；余额不足与系统失败是两种不同状态，业务层需区别对待
     */
    PayResult pay(PayCommand cmd);

    /**
     * 退款。实现须保证幂等：同一退款指令重复调用不得重复出账。
     */
    RefundResult refund(RefundCommand cmd);

    /**
     * 验签并解析渠道回调报文。
     *
     * @param params 回调参数（表单或查询参数展平后的键值对）
     * @return 解析结果；{@link PayNotify#isSignValid()} 为 false 时调用方必须拒绝处理
     */
    PayNotify parseNotify(Map<String, String> params);
}
