package com.ruoyi.campus.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.campus.payment.PaymentChannelProperties;
import com.ruoyi.campus.payment.PaymentGatewayResolver;

/**
 * 校园淘业务指标（校园淘）
 *
 * <p>只登记三类"出事必须立刻知道"的指标，不做全量埋点：</p>
 * <ul>
 *   <li>{@code campus.payment.attempts}——支付尝试数，按 channel / mode / result 打标；</li>
 *   <li>{@code campus.refund.attempts}——退款尝试数，同上；</li>
 *   <li>{@code campus.payment.channel.available}——当前渠道是否可用（1/0）。</li>
 * </ul>
 *
 * <p>为什么这三类：支付失败率升高意味着用户付不出钱，退款失败率升高意味着钱退不回去，
 * 渠道不可用意味着整条交易链路直接断掉。其余指标（QPS、延迟）由 actuator 的
 * {@code http.server.requests} 与 Hikari 指标自带，不必重复登记。</p>
 *
 * <p><b>计数放在 finally 里</b>：渠道抛异常时同样计入 failed。若只在成功路径打点，
 * "渠道挂了"这一最该告警的场景反而没有任何数据。</p>
 *
 * @author campus-tao
 */
@Component
public class CampusMetrics {

    /** 支付尝试计数（counter） */
    public static final String PAYMENT_ATTEMPTS = "campus.payment.attempts";

    /** 退款尝试计数（counter） */
    public static final String REFUND_ATTEMPTS = "campus.refund.attempts";

    /** 当前渠道可用性（gauge，1=可用 0=不可用） */
    public static final String CHANNEL_AVAILABLE = "campus.payment.channel.available";

    static final String TAG_CHANNEL = "channel";
    static final String TAG_MODE = "mode";
    static final String TAG_RESULT = "result";
    static final String RESULT_SUCCESS = "success";
    static final String RESULT_FAILED = "failed";

    private final MeterRegistry registry;
    private final PaymentGatewayResolver resolver;
    private final PaymentChannelProperties props;

    @Autowired
    public CampusMetrics(MeterRegistry registry, PaymentGatewayResolver resolver, PaymentChannelProperties props) {
        this.registry = registry;
        this.resolver = resolver;
        this.props = props;
        // 用 lambda 取值而非启动时取一次快照：渠道可用性会随配置/商户参数变化，
        // 抓取时实时求值才能反映"现在能不能付"。
        Gauge.builder(CHANNEL_AVAILABLE, this, CampusMetrics::channelAvailable)
                .description("当前支付渠道是否可用：1=可用，0=不可用")
                .tag(TAG_CHANNEL, String.valueOf(props.getChannel()))
                .register(registry);
    }

    /** 当前配置渠道是否可解析（未注册或未启用都算不可用） */
    private double channelAvailable() {
        return resolver.byChannel(props.getChannel()) != null ? 1d : 0d;
    }

    /** 记录一次支付尝试 */
    public void payment(String channel, String mode, boolean success) {
        count(PAYMENT_ATTEMPTS, channel, mode, success);
    }

    /** 记录一次退款尝试 */
    public void refund(String channel, String mode, boolean success) {
        count(REFUND_ATTEMPTS, channel, mode, success);
    }

    private void count(String name, String channel, String mode, boolean success) {
        Counter.builder(name)
                .tag(TAG_CHANNEL, channel)
                .tag(TAG_MODE, mode)
                .tag(TAG_RESULT, success ? RESULT_SUCCESS : RESULT_FAILED)
                .register(registry)
                .increment();
    }
}
