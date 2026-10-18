package com.ruoyi.campus.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.campus.payment.ICampusPaymentService;
import com.ruoyi.campus.payment.IPaymentGateway;
import com.ruoyi.campus.payment.PayNotify;
import com.ruoyi.campus.payment.PaymentGatewayResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付渠道 Controller（校园淘）
 *
 * <p>提供两类端点：</p>
 * <ul>
 *   <li>{@code GET /campus/payment/channel}——查当前生效渠道，供前端展示与运维排查；</li>
 *   <li>{@code POST /campus/payment/notify/{channel}}——渠道回调入口。</li>
 * </ul>
 *
 * <p><b>回调端点的安全姿态</b>：它必须匿名可访问（第三方服务器来调，带不了我们的登录态），
 * 所以安全性完全押在验签上——{@link IPaymentGateway#parseNotify} 验签失败即整体拒绝，
 * 且在 {@code CampusPaymentServiceImpl} 里再拦一次（验签失败直接抛异常）。
 * 两层都拒，避免"某个渠道忘了写验签"变成"回调不校验就放行"的漏洞。</p>
 *
 * @author campus-tao
 */
@RestController
@RequestMapping("/campus/payment")
public class CampusPaymentController extends BaseController {

    /** 配置的渠道不可解析（未注册或未启用），既不是沙箱也不是真实 */
    public static final String MODE_UNAVAILABLE = "unavailable";

    @Autowired
    private ICampusPaymentService paymentService;

    @Autowired
    private PaymentGatewayResolver resolver;

    /**
     * 当前生效支付渠道。
     *
     * <p>{@code mode} 是给用户看的资金语义，与 {@code channel} 分开：渠道名是运维概念，
     * 用户关心的是"这一下会不会扣我的钱"。两者分开后，将来接真实渠道只需换实现，
     * 界面提示不必跟着改。</p>
     *
     * <p><b>取不到生效渠道时不得回落成 sandbox</b>：把"渠道不可用"标成"沙箱演示"，
     * 会让运维以为系统正跑在正常沙箱上，而实际上支付根本走不通。故单列 {@code unavailable}。</p>
     */
    @GetMapping("/channel")
    public AjaxResult channel() {
        String channel = paymentService.currentChannel();
        IPaymentGateway active = resolver.byChannel(channel);

        Map<String, Object> data = new HashMap<>(8);
        data.put("channel", channel);
        // 注册 ≠ 可用：wechat 在未填商户参数时已注册但 enabled()=false，
        // 若只给 registered，前端会把"选了走不通"的渠道当成可用选项展示。
        data.put("registered", resolver.availableChannels());
        data.put("enabled", resolver.enabledChannels());
        data.put("mode", active == null ? MODE_UNAVAILABLE : active.mode());
        data.put("realMoney", active != null && active.realMoney());
        data.put("notice", noticeOf(active));
        return AjaxResult.success(data);
    }

    /** 面向用户的资金语义提示，单一来源放在服务端，避免前后端各写一份文案后走样 */
    private String noticeOf(IPaymentGateway active) {
        if (active == null) {
            return "当前支付渠道不可用（未注册或商户参数未配置齐），支付与退款将无法完成";
        }
        if (active.realMoney()) {
            return "真实资金渠道：支付与退款会产生实际资金流动";
        }
        return "沙箱演示环境：支付与退款仅记账于站内余额，不产生真实扣款";
    }

    /**
     * 渠道支付成功回调。
     *
     * <p>渠道名取自路径而非配置——回调方自带它以为在调用的渠道，信配置会让
     * "渠道写错但配置是沙箱"的情况下走到错误实现上。</p>
     *
     * <p>返回 200 表示已接收（渠道会停止重试）；验签失败也返回 200 但 body 带错误码，
     * 避免渠道因收到 5xx 而无限重推——真实接入时应按渠道协议区分。</p>
     */
    @Anonymous
    @PostMapping("/notify/{channel}")
    public AjaxResult notify(@PathVariable String channel, @RequestParam Map<String, String> params) {
        IPaymentGateway gateway = resolver.byChannel(channel);
        if (gateway == null) {
            return AjaxResult.error("支付渠道不存在或未启用: " + channel);
        }
        PayNotify notify = gateway.parseNotify(params);
        if (!notify.isSignValid()) {
            return AjaxResult.error("回调验签失败，已拒绝处理");
        }
        boolean advanced = paymentService.handlePaidNotify(channel, notify);
        return AjaxResult.success("已接收" + (advanced ? "（本次推进了订单状态）" : "（重复回调，已忽略）"));
    }
}
