package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付沙箱账户 Mapper（校园淘）
 */
@Mapper
public interface CampusAccountMapper {
    int insertCampusAccount(CampusAccount account);
    int updateCampusAccount(CampusAccount account);
    CampusAccount selectByUserId(Long userId);
    List<CampusAccount> selectAccountList(CampusAccount account);

    /**
     * 原子扣减余额（余额充足才扣，防并发超扣/超卖）
     *
     * @return 影响行数，0 表示余额不足
     */
    int decreaseBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子增加余额（退款入账）
     */
    int increaseBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
