package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.ruoyi.campus.controller.CampusPaymentController;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.observability.CampusMetrics;
import com.ruoyi.campus.payment.IPaymentGateway;
import com.ruoyi.campus.payment.PayCommand;
import com.ruoyi.campus.payment.PayNotify;
import com.ruoyi.campus.payment.PayResult;
import com.ruoyi.campus.payment.PaymentChannelProperties;
import com.ruoyi.campus.payment.PaymentGatewayResolver;
import com.ruoyi.campus.payment.RefundCommand;
import com.ruoyi.campus.payment.RefundResult;
import com.ruoyi.campus.payment.SandboxPaymentGateway;
import com.ruoyi.campus.payment.WechatPayGateway;
import com.ruoyi.campus.payment.impl.CampusPaymentServiceImpl;
import com.ruoyi.campus.service.ICampusAccountService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;

/**
 * 支付网关抽象层测试（校园淘）
 *
 * <p>覆盖四类：渠道路由的选择与失败语义、沙箱网关的成功/余额不足/退款、
 * 微信骨架的"未实现必须显式失败"、以及回调处理的幂等与金额核对。</p>
 *
 * <p>其中两条负向断言是本类的重点：</p>
 * <ul>
 *   <li>沙箱渠道的 {@code parseNotify} 必须判验签失败——没有签名体系不等于不校验；</li>
 *   <li>微信骨架未写验签前也必须判验签失败——不能因为"还没实现"就放行回调。</li>
 * </ul>
 * 这两条一旦回归，等于把"伪造支付成功回调"的门打开。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("支付网关抽象层")
class CampusPaymentGatewayTest {

    @Mock
    private ICampusAccountService accountService;

    @Mock
    private CampusOrderMapper orderMapper;

    // 构造器注入的协作者，Mockito 的 @InjectMocks 可直接按构造器参数匹配注入
    @InjectMocks
    private SandboxPaymentGateway sandbox;

    // 这两个不用 @InjectMocks：它们依赖的是"真实对象"而非 mock
    // （resolver 需要 List<IPaymentGateway>，props 是需要改字段来验证分支的配置 POJO），
    // 由 setUp() 显式装配，与 Spring 容器的装配方式一致。
    private WechatPayGateway wechat;
    private CampusPaymentServiceImpl paymentService;
    private PaymentGatewayResolver resolver;
    private PaymentChannelProperties props;
    private SimpleMeterRegistry meterRegistry;
    private CampusMetrics metrics;

    @BeforeEach
    void setUp() {
        props = new PaymentChannelProperties();
        wechat = new WechatPayGateway(props);
        resolver = new PaymentGatewayResolver(Arrays.asList(sandbox, wechat), props);
        meterRegistry = new SimpleMeterRegistry();
        metrics = new CampusMetrics(meterRegistry, resolver, props);
        paymentService = new CampusPaymentServiceImpl(resolver, props, orderMapper, metrics);
    }

    // ---------------- 渠道路由 ----------------

    @Test
    @DisplayName("默认渠道为 sandbox，路由解析成功")
    void resolve_defaultChannelIsSandbox() {
        assertEquals("sandbox", props.getChannel());

        IPaymentGateway gw = resolver.resolve();

        assertNotNull(gw);
        assertEquals("sandbox", gw.channel());
        assertTrue(gw.enabled());
    }

    @Test
    @DisplayName("渠道名写错时抛异常，不静默回落到别的渠道")
    void resolve_unknownChannelThrows() {
        props.setChannel("alipay-not-registered");

        ServiceException ex = assertThrows(ServiceException.class, () -> resolver.resolve());

        assertTrue(ex.getMessage().contains("未配置的支付渠道"));
        assertTrue(ex.getMessage().contains("sandbox"), "错误信息应列出可用渠道便于排查");
    }

    @Test
    @DisplayName("微信商户参数未填齐时渠道不可用，路由明确报错而非回落沙箱")
    void resolve_wechatNotConfiguredThrows() {
        props.setChannel("wechat");
        // enabled 默认 false 且商户参数为空

        ServiceException ex = assertThrows(ServiceException.class, () -> resolver.resolve());

        assertTrue(ex.getMessage().contains("支付渠道未启用"));
    }

    @Test
    @DisplayName("微信商户参数填齐且启用后可被路由解析")
    void resolve_wechatConfiguredResolves() {
        PaymentChannelProperties.Wechat w = props.getWechat();
        w.setEnabled(true);
        w.setAppId("wxapp");
        w.setMchId("1600000000");
        w.setApiV3Key("key");
        w.setSerialNo("serial");
        w.setPrivateKeyPath("/keys/apiclient_key.pem");
        props.setChannel("wechat");

        IPaymentGateway gw = resolver.resolve();

        assertEquals("wechat", gw.channel());
        assertTrue(gw.enabled());
    }

    @Test
    @DisplayName("按渠道名取网关：回调方自带渠道标识，与配置解耦")
    void byChannel_independentOfConfig() {
        props.setChannel("wechat"); // 配置指向微信，但按名取沙箱仍应拿到沙箱

        IPaymentGateway gw = resolver.byChannel("sandbox");

        assertNotNull(gw);
        assertEquals("sandbox", gw.channel());
    }

    @Test
    @DisplayName("按渠道名取不存在的渠道返回 null，由调用方拒绝")
    void byChannel_unknownReturnsNull() {
        assertNotNull(resolver.byChannel("sandbox"));
        assertEquals(null, resolver.byChannel("nope"));
    }

    // ---------------- 沙箱网关 ----------------

    @Test
    @DisplayName("沙箱扣款成功返回 SUCCESS 并带上渠道交易号")
    void sandbox_paySuccess() {
        when(accountService.pay(anyLong(), any(BigDecimal.class))).thenReturn(true);

        PayResult r = sandbox.pay(PayCommand.of(2L, "CT001", new BigDecimal("60.00")));

        assertTrue(r.isSuccess());
        assertEquals(PayResult.Status.SUCCESS, r.getStatus());
        assertNotNull(r.getChannelTradeNo());
        verify(accountService).pay(2L, new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("沙箱余额不足返回 INSUFFICIENT_BALANCE（业务性拒绝，非异常）")
    void sandbox_payInsufficientBalance() {
        when(accountService.pay(anyLong(), any(BigDecimal.class))).thenReturn(false);

        PayResult r = sandbox.pay(PayCommand.of(2L, "CT001", new BigDecimal("60.00")));

        assertFalse(r.isSuccess());
        assertEquals(PayResult.Status.INSUFFICIENT_BALANCE, r.getStatus());
        assertNotNull(r.getFailReason());
    }

    @Test
    @DisplayName("沙箱退款成功")
    void sandbox_refundSuccess() {
        RefundResult r = sandbox.refund(RefundCommand.of(2L, "CT001", null, new BigDecimal("60.00")));

        assertTrue(r.isSuccess());
        assertNotNull(r.getRefundNo());
        verify(accountService).refund(2L, new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("负向：沙箱渠道无签名体系，回调一律判验签失败")
    void sandbox_parseNotifyAlwaysInvalidSign() {
        Map<String, String> params = new HashMap<>();
        params.put("orderNo", "CT001");
        params.put("paid", "true");

        PayNotify n = sandbox.parseNotify(params);

        assertFalse(n.isSignValid(), "没有签名体系不能等同于不校验");
    }

    // ---------------- 微信骨架 ----------------

    @Test
    @DisplayName("负向：微信骨架未实现，扣款显式失败并说明原因")
    void wechat_payFailsExplicitly() {
        PayResult r = wechat.pay(PayCommand.of(2L, "CT001", new BigDecimal("60.00")));

        assertFalse(r.isSuccess());
        assertEquals(PayResult.Status.FAILED, r.getStatus());
        assertNotNull(r.getFailReason());
        assertTrue(r.getFailReason().contains("尚未实现"));
    }

    @Test
    @DisplayName("负向：微信骨架未写验签，回调一律判验签失败")
    void wechat_parseNotifyInvalidSign() {
        Map<String, String> params = new HashMap<>();
        params.put("orderNo", "CT001");

        PayNotify n = wechat.parseNotify(params);

        assertFalse(n.isSignValid(), "未实现验签绝不能放行回调");
    }

    // ---------------- 回调处理 ----------------

    private CampusOrder orderWithStatus(int status, String amount) {
        CampusOrder o = new CampusOrder();
        o.setOrderNo("CT001");
        o.setUserId(2L);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal(amount));
        return o;
    }

    @Test
    @DisplayName("回调验签通过且订单待付款：推进为已支付并返回 true")
    void handlePaidNotify_advancesWaitPayOrder() {
        when(orderMapper.selectOrderByOrderNo("CT001")).thenReturn(orderWithStatus(0, "60.00"));

        PayNotify n = PayNotify.of(true, "CT001", "WX4200001", true, new BigDecimal("60.00"));
        boolean advanced = paymentService.handlePaidNotify("wechat", n);

        assertTrue(advanced);
        verify(orderMapper).updateCampusOrder(any(CampusOrder.class));
    }

    @Test
    @DisplayName("幂等：订单已支付时重复回调返回 false 且不再改状态")
    void handlePaidNotify_idempotentOnAlreadyPaid() {
        when(orderMapper.selectOrderByOrderNo("CT001")).thenReturn(orderWithStatus(1, "60.00"));

        PayNotify n = PayNotify.of(true, "CT001", "WX4200001", true, new BigDecimal("60.00"));
        boolean advanced = paymentService.handlePaidNotify("wechat", n);

        assertFalse(advanced);
        verify(orderMapper, never()).updateCampusOrder(any(CampusOrder.class));
    }

    @Test
    @DisplayName("金额核对：回调金额与订单金额不一致时抛异常拒绝")
    void handlePaidNotify_amountMismatchRejected() {
        when(orderMapper.selectOrderByOrderNo("CT001")).thenReturn(orderWithStatus(0, "60.00"));

        PayNotify n = PayNotify.of(true, "CT001", "WX4200001", true, new BigDecimal("0.01"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> paymentService.handlePaidNotify("wechat", n));

        assertTrue(ex.getMessage().contains("金额不一致"));
        verify(orderMapper, never()).updateCampusOrder(any(CampusOrder.class));
    }

    @Test
    @DisplayName("负向：验签失败的回调直接抛异常，不查库不改状态")
    void handlePaidNotify_invalidSignRejected() {
        PayNotify n = PayNotify.invalidSign();

        ServiceException ex = assertThrows(ServiceException.class,
                () -> paymentService.handlePaidNotify("wechat", n));

        assertTrue(ex.getMessage().contains("验签失败"));
        verify(orderMapper, never()).selectOrderByOrderNo(any());
    }

    @Test
    @DisplayName("回调告知未支付时不推进状态")
    void handlePaidNotify_notPaidDoesNothing() {
        PayNotify n = PayNotify.of(true, "CT001", "WX4200001", false, null);
        boolean advanced = paymentService.handlePaidNotify("wechat", n);

        assertFalse(advanced);
        verify(orderMapper, never()).updateCampusOrder(any(CampusOrder.class));
    }

    @Test
    @DisplayName("回调对应订单不存在时抛异常（防止伪造单号推进状态）")
    void handlePaidNotify_unknownOrderThrows() {
        when(orderMapper.selectOrderByOrderNo("CT999")).thenReturn(null);

        PayNotify n = PayNotify.of(true, "CT999", "WX4200001", true, new BigDecimal("60.00"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> paymentService.handlePaidNotify("wechat", n));

        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("availableChannels 暴露已注册渠道，便于运维排查")
    void availableChannels_listsRegistered() {
        assertEquals(2, resolver.availableChannels().size());
        assertTrue(resolver.availableChannels().contains("sandbox"));
        assertTrue(resolver.availableChannels().contains("wechat"));
    }

    @Test
    @DisplayName("负向：已注册≠可用——微信未配置时不得出现在 enabledChannels")
    void enabledChannels_excludesUnconfigured() {
        // 若只暴露 availableChannels，前端会把"选了走不通"的微信当成可用选项展示
        assertTrue(resolver.enabledChannels().contains("sandbox"));
        assertFalse(resolver.enabledChannels().contains("wechat"));
        assertTrue(resolver.availableChannels().contains("wechat"));
    }

    @Test
    @DisplayName("微信商户参数填齐后进入 enabledChannels")
    void enabledChannels_includesConfiguredWechat() {
        PaymentChannelProperties.Wechat w = props.getWechat();
        w.setEnabled(true);
        w.setAppId("wx1");
        w.setMchId("m1");
        w.setApiV3Key("k");
        w.setSerialNo("s");
        w.setPrivateKeyPath("apiclient_key.pem");
        assertTrue(w.isConfigured());

        assertTrue(resolver.enabledChannels().contains("wechat"));
        assertTrue(resolver.enabledChannels().contains("sandbox"));
    }

    @Test
    @DisplayName("配置默认值：微信未启用且商户参数为空")
    void wechat_defaultsDisabledAndUnconfigured() {
        PaymentChannelProperties.Wechat w = new PaymentChannelProperties.Wechat();

        assertFalse(w.isEnabled());
        assertFalse(w.isConfigured());
    }

    // ---------------- 资金语义声明（mode） ----------------

    @Test
    @DisplayName("沙箱渠道声明不涉及真实资金")
    void sandbox_declaresNotRealMoney() {
        assertFalse(sandbox.realMoney(), "沙箱只记站内余额，不得声明为真实资金");
    }

    @Test
    @DisplayName("微信渠道按设计意图声明为真实资金")
    void wechat_declaresRealMoney() {
        assertTrue(wechat.realMoney(), "真实渠道若标成沙箱，界面会误导用户以为不扣钱");
    }

    @Test
    @DisplayName("渠道状态接口：沙箱模式返回 mode=sandbox 与不扣款提示")
    void channel_sandboxMode() {
        Map<String, Object> data = channelData();

        assertEquals("sandbox", data.get("channel"));
        assertEquals("sandbox", data.get("mode"));
        assertEquals(Boolean.FALSE, data.get("realMoney"));
        assertTrue(String.valueOf(data.get("notice")).contains("不产生真实扣款"));
    }

    @Test
    @DisplayName("渠道状态接口：真实渠道返回 mode=real 与扣款提示")
    void channel_realMode() {
        PaymentChannelProperties.Wechat w = props.getWechat();
        w.setEnabled(true);
        w.setAppId("wx1");
        w.setMchId("m1");
        w.setApiV3Key("k");
        w.setSerialNo("s");
        w.setPrivateKeyPath("apiclient_key.pem");
        props.setChannel("wechat");

        Map<String, Object> data = channelData();

        assertEquals("wechat", data.get("channel"));
        assertEquals("real", data.get("mode"));
        assertEquals(Boolean.TRUE, data.get("realMoney"));
        assertTrue(String.valueOf(data.get("notice")).contains("实际资金流动"));
    }

    @Test
    @DisplayName("负向：配置的渠道不可用时 mode=unavailable，不得回落成 sandbox")
    void channel_unavailableMode() {
        // 渠道名指向未启用的微信：byChannel 返回 null，此时若回落成 sandbox，
        // 界面会显示"沙箱演示、可正常支付"，而实际上支付根本走不通。
        props.setChannel("wechat");

        Map<String, Object> data = channelData();

        assertEquals("unavailable", data.get("mode"));
        assertEquals(Boolean.FALSE, data.get("realMoney"));
        assertTrue(String.valueOf(data.get("notice")).contains("不可用"));
    }

    // ---------------- 业务指标（Prometheus 抓取口径） ----------------

    @Test
    @DisplayName("支付成功计入 campus.payment.attempts{result=success}")
    void payment_metricCountsSuccess() {
        when(accountService.pay(2L, new BigDecimal("60.00"))).thenReturn(true);

        paymentService.pay(PayCommand.of(2L, "CT001", new BigDecimal("60.00")));

        assertEquals(1d, counter(CampusMetrics.PAYMENT_ATTEMPTS, "sandbox", "sandbox", "success"));
    }

    @Test
    @DisplayName("渠道不可用时支付抛异常，且计入 {mode=unavailable,result=failed}")
    void payment_metricCountsChannelUnavailable() {
        props.setChannel("wechat");
        PayCommand cmd = PayCommand.of(2L, "CT001", new BigDecimal("60.00"));

        assertThrows(ServiceException.class, () -> paymentService.pay(cmd));

        // 这是本类最关键的一条：若只在拿到网关之后打点，"渠道挂了"在指标上是空白
        assertEquals(1d, counter(CampusMetrics.PAYMENT_ATTEMPTS, "wechat", "unavailable", "failed"));
    }

    @Test
    @DisplayName("余额不足计入 failed（业务失败也要可见，否则失败率永远为 0）")
    void payment_metricCountsInsufficientBalance() {
        when(accountService.pay(2L, new BigDecimal("60.00"))).thenReturn(false);

        paymentService.pay(PayCommand.of(2L, "CT001", new BigDecimal("60.00")));

        assertEquals(1d, counter(CampusMetrics.PAYMENT_ATTEMPTS, "sandbox", "sandbox", "failed"));
    }

    @Test
    @DisplayName("退款失败计入 campus.refund.attempts{result=failed}")
    void refund_metricCountsFailure() {
        props.setChannel("wechat");
        RefundCommand cmd = RefundCommand.of(2L, "CT001", null, new BigDecimal("60.00"));

        assertThrows(ServiceException.class, () -> paymentService.refund(cmd));

        assertEquals(1d, counter(CampusMetrics.REFUND_ATTEMPTS, "wechat", "unavailable", "failed"));
    }

    @Test
    @DisplayName("渠道可用性 gauge：正常 1，配置不可解析 0")
    void channelAvailableGauge() {
        assertEquals(1d, meterRegistry.get(CampusMetrics.CHANNEL_AVAILABLE).gauge().value());

        props.setChannel("wechat");
        assertEquals(0d, meterRegistry.get(CampusMetrics.CHANNEL_AVAILABLE).gauge().value(),
                "配置成未启用的渠道时 gauge 必须掉到 0，否则告警规则不会触发");
    }

    /** 按 channel/mode/result 三元组取计数器的当前值（未产生过则为 0） */
    private double counter(String name, String channel, String mode, String result) {
        return meterRegistry.get(name)
                .tag("channel", channel)
                .tag("mode", mode)
                .tag("result", result)
                .counter()
                .count();
    }

    /** 直接调用 Controller 方法取 data 体（字段注入沿用项目既有写法，用反射装配） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> channelData() {
        CampusPaymentController controller = new CampusPaymentController();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        ReflectionTestUtils.setField(controller, "resolver", resolver);
        AjaxResult result = controller.channel();
        return (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
    }
}
