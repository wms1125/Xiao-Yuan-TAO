package com.ruoyi.campus.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付渠道配置（校园淘）
 *
 * <p>配置前缀 {@code campus.payment}，示例：</p>
 * <pre>
 * campus:
 *   payment:
 *     channel: sandbox        # sandbox | wechat
 *     wechat:
 *       enabled: false        # 未配置真实商户前必须为 false
 *       appId: ""
 *       mchId: ""
 *       apiV3Key: ""
 *       serialNo: ""
 *       privateKeyPath: ""
 *       notifyUrl: ""
 * </pre>
 *
 * <p>把渠道密钥放在独立的嵌套配置里（而非散落在 application.yml 各处），
 * 是为了让"启用真实渠道"这件事有唯一入口——改配置即可，不需要搜代码。</p>
 *
 * @author campus-tao
 */
@Component
@ConfigurationProperties(prefix = "campus.payment")
public class PaymentChannelProperties {

    /** 当前启用渠道，与各 IPaymentGateway#channel() 对应 */
    private String channel = "sandbox";

    private Wechat wechat = new Wechat();

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public void setWechat(Wechat wechat) {
        this.wechat = wechat;
    }

    /**
     * 微信支付配置。enabled 默认 false——未填齐商户参数就置 true 会在首笔支付时
     * 以"渠道未配置"失败，属可预期的显式失败，好过静默走到一个空实现。
     */
    public static class Wechat {

        private boolean enabled = false;
        private String appId = "";
        private String mchId = "";
        private String apiV3Key = "";
        private String serialNo = "";
        private String privateKeyPath = "";
        private String notifyUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getMchId() {
            return mchId;
        }

        public void setMchId(String mchId) {
            this.mchId = mchId;
        }

        public String getApiV3Key() {
            return apiV3Key;
        }

        public void setApiV3Key(String apiV3Key) {
            this.apiV3Key = apiV3Key;
        }

        public String getSerialNo() {
            return serialNo;
        }

        public void setSerialNo(String serialNo) {
            this.serialNo = serialNo;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getNotifyUrl() {
            return notifyUrl;
        }

        public void setNotifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
        }

        /** 商户关键参数是否填齐——enabled() 的判定依据 */
        public boolean isConfigured() {
            return isNotBlank(appId) && isNotBlank(mchId) && isNotBlank(apiV3Key)
                    && isNotBlank(serialNo) && isNotBlank(privateKeyPath);
        }

        private static boolean isNotBlank(String s) {
            return s != null && !s.trim().isEmpty();
        }
    }
}
