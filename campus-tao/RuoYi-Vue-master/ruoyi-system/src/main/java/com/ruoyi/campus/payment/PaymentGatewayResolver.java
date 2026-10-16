package com.ruoyi.campus.payment;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付渠道路由（校园淘）
 *
 * <p>按配置 {@code campus.payment.channel} 选出当前生效的网关。
 * 所有网关实现都注册为 Spring Bean，此处只做"选哪个"，因此切换渠道只改配置、不改代码，
 * 也不需要在业务代码里写 if-else 分支。</p>
 *
 * <p><b>失败要显式</b>：渠道名写错、或渠道存在但未启用（如微信商户参数没填齐），
 * 都必须抛异常而不是回落到某个默认渠道——静默回落会让"以为在用微信、实际走的沙箱"
 * 这类资金错配问题极难排查。</p>
 *
 * @author campus-tao
 */
@Component
public class PaymentGatewayResolver {

    private final Map<String, IPaymentGateway> byChannel;
    private final PaymentChannelProperties props;

    @Autowired
    public PaymentGatewayResolver(List<IPaymentGateway> gateways, PaymentChannelProperties props) {
        this.byChannel = gateways.stream()
                .collect(Collectors.toMap(IPaymentGateway::channel, Function.identity()));
        this.props = props;
    }

    /** 取当前配置渠道的网关；渠道未配置或未启用时抛异常 */
    public IPaymentGateway resolve() {
        String channel = props.getChannel();
        IPaymentGateway gw = byChannel.get(channel);
        if (gw == null) {
            throw new ServiceException("未配置的支付渠道: " + channel + "，可用渠道: " + byChannel.keySet());
        }
        if (!gw.enabled()) {
            throw new ServiceException("支付渠道未启用: " + channel + "（请检查该渠道的商户参数配置）");
        }
        return gw;
    }

    /** 按渠道名取网关，供回调端点使用（回调方自带渠道标识，不能信配置） */
    public IPaymentGateway byChannel(String channel) {
        IPaymentGateway gw = byChannel.get(channel);
        if (gw == null || !gw.enabled()) {
            return null;
        }
        return gw;
    }

    /** 已注册的渠道名，用于错误提示与运维排查（**含未启用**，不代表可用） */
    public java.util.Set<String> availableChannels() {
        return byChannel.keySet();
    }

    /**
     * 真正可用的渠道名：已注册且 {@link IPaymentGateway#enabled()} 为真。
     *
     * <p>与 {@link #availableChannels()} 的区别就在这里：后者会列出"已注册但商户参数没填齐"
     * 的渠道，若直接拿它当"可用渠道"渲染给用户，会出现"选了微信却走不通"的资金错配。
     */
    public java.util.Set<String> enabledChannels() {
        return byChannel.entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
