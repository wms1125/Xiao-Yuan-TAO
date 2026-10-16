package com.ruoyi.campus.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付渠道骨架（校园淘）
 *
 * <p><b>当前状态：未实现，默认不启用。</b> 本类只把接口契约、配置入口与失败语义占好位置，
 * 真正的 APIv3 调用（下单 / 查询 / 退款 / 回调验签）尚未编写。</p>
 *
 * <p>为什么留骨架而不是等要做时再建：支付渠道是"改动面会蔓延到订单与售后"的横切关注点。
 * 先把接口与渠道路由铺好，后续接真实渠道就是"填这个类 + 改一行配置"，
 * 而不需要再动一次业务代码。这也让"支持多渠道"这件事从设计意图变成可验证的事实。</p>
 *
 * <p><b>启用前置条件</b>：{@code campus.payment.wechat.enabled=true} 且商户参数填齐
 * （见 {@link PaymentChannelProperties.Wechat#isConfigured()}）。未填齐时
 * {@link #enabled()} 返回 false，渠道路由会拒绝解析并给出明确错误，
 * 不会静默走到一个空实现上。</p>
 *
 * <p>待办（接入真实微信支付时）：</p>
 * <ol>
 *   <li>{@link #pay}：调 JSAPI/Native 下单，落渠道交易号；</li>
 *   <li>{@link #parseNotify}：用平台证书验签（Wechatpay-Signature / Serial / Timestamp / Nonce），
 *       并用 AEAD_AES_256_GCM 解密 resource；</li>
 *   <li>{@link #refund}：调退款接口，落退款单号，处理异步退款结果回调；</li>
 *   <li>补退款单号去重表，保证退款幂等。</li>
 * </ol>
 *
 * @author campus-tao
 */
@Component
public class WechatPayGateway implements IPaymentGateway {

    /** 未实现渠道的统一报错文案，便于在日志里一眼定位 */
    static final String NOT_IMPLEMENTED = "微信支付渠道尚未实现（骨架阶段）：需先完成 APIv3 下单/查询/退款/回调验签，并配置商户参数";

    private final PaymentChannelProperties props;

    /**
     * 用构造器注入而非字段注入：本类的 enabled() 强依赖配置对象，
     * 构造器注入让依赖在测试里可显式传入，无需反射即可验证"未配置时不启用"这条关键分支。
     */
    @Autowired
    public WechatPayGateway(PaymentChannelProperties props) {
        this.props = props;
    }

    @Override
    public String channel() {
        return "wechat";
    }

    @Override
    public boolean enabled() {
        return props.getWechat().isEnabled() && props.getWechat().isConfigured();
    }

    @Override
    public PayResult pay(PayCommand cmd) {
        return PayResult.failed(NOT_IMPLEMENTED);
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        return RefundResult.failed(NOT_IMPLEMENTED);
    }

    @Override
    public PayNotify parseNotify(Map<String, String> params) {
        // 未实现验签前一律判为验签失败——绝不能因为"还没写验签"就放行回调
        return PayNotify.invalidSign();
    }
}
