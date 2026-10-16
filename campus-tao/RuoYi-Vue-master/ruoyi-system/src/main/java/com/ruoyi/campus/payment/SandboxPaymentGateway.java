package com.ruoyi.campus.payment;

import com.ruoyi.campus.service.ICampusAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 沙箱支付渠道（校园淘）
 *
 * <p>扣的是站内余额（{@link ICampusAccountService}），是当前默认且唯一真正跑通的渠道。
 * 它存在的意义不只是"能用"，而是把原先散在订单/售后逻辑里的余额操作收拢到一处，
 * 让业务代码从此只面向 {@link IPaymentGateway}。</p>
 *
 * <p><b>退款幂等性说明</b>：{@code increaseBalance} 是纯累加，本身不幂等。
 * 幂等由上层保证——退款动作只发生在订单状态流转的那一次事务里
 * （取消已付款订单 / 售后审核通过），状态机保证同一订单不会重复进入退款分支。
 * 若将来接入异步退款回调，需在此处加"退款单号去重"表。</p>
 *
 * @author campus-tao
 */
@Component
public class SandboxPaymentGateway implements IPaymentGateway {

    /** 沙箱渠道不依赖渠道侧交易号，用订单号派生一个稳定值供对账占位 */
    private static final String TRADE_NO_PREFIX = "SBX";

    @Autowired
    private ICampusAccountService accountService;

    @Override
    public String channel() {
        return "sandbox";
    }

    @Override
    public boolean enabled() {
        // 沙箱渠道始终可用：它是内置能力，不依赖外部商户配置
        return true;
    }

    @Override
    public PayResult pay(PayCommand cmd) {
        boolean ok = accountService.pay(cmd.getUserId(), cmd.getAmount());
        if (!ok) {
            return PayResult.insufficientBalance("余额不足");
        }
        return PayResult.success(TRADE_NO_PREFIX + cmd.getOrderNo());
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        accountService.refund(cmd.getUserId(), cmd.getAmount());
        return RefundResult.success("RF" + cmd.getOrderNo());
    }

    @Override
    public PayNotify parseNotify(Map<String, String> params) {
        // 沙箱渠道没有第三方回调方，不存在可验签的报文。
        // 返回验签失败，使任何打到该端点上的沙箱回调都被拒绝——
        // 宁可拒绝，也不能让"没有签名体系"变成"不校验就放行"的漏洞。
        return PayNotify.invalidSign();
    }
}
