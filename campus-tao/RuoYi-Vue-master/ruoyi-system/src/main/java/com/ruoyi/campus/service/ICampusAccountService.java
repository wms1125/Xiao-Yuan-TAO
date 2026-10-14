package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusAccount;
import java.math.BigDecimal;

/**
 * 支付沙箱账户服务（校园淘）
 */
public interface ICampusAccountService {
    /** 获取或创建账户 */
    CampusAccount getOrCreate(Long userId, String userName);
    /** 查余额 */
    BigDecimal getBalance(Long userId);
    /** 扣款：余额不足返回 false */
    boolean pay(Long userId, BigDecimal amount);
    /** 退款（回余额） */
    void refund(Long userId, BigDecimal amount);
}
