package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusAccount;
import com.ruoyi.campus.mapper.CampusAccountMapper;
import com.ruoyi.campus.service.ICampusAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CampusAccountServiceImpl implements ICampusAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Autowired
    private CampusAccountMapper accountMapper;

    @Override
    @Transactional
    public CampusAccount getOrCreate(Long userId, String userName) {
        CampusAccount acc = accountMapper.selectByUserId(userId);
        if (acc == null) {
            acc = new CampusAccount();
            acc.setUserId(userId);
            acc.setUserName(userName);
            acc.setBalance(ZERO);
            acc.setFreeze(ZERO);
            accountMapper.insertCampusAccount(acc);
        }
        return acc;
    }

    @Override
    public BigDecimal getBalance(Long userId) {
        CampusAccount acc = accountMapper.selectByUserId(userId);
        return acc == null ? ZERO : (acc.getBalance() == null ? ZERO : acc.getBalance());
    }

    @Override
    @Transactional
    public boolean pay(Long userId, BigDecimal amount) {
        // 确保账户行存在（新用户首次支付）
        getOrCreate(userId, null);
        // 原子扣减：balance >= amount 才会扣成功，防并发超扣
        return accountMapper.decreaseBalance(userId, amount) > 0;
    }

    @Override
    @Transactional
    public void refund(Long userId, BigDecimal amount) {
        // 确保账户行存在（新用户首次入账）
        getOrCreate(userId, null);
        // 原子入账
        accountMapper.increaseBalance(userId, amount);
    }
}
