package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusAftersale;
import java.util.List;

/**
 * 售后服务（校园淘）
 * 状态：0待审核 1通过 2拒绝 3已完成
 */
public interface ICampusAftersaleService {
    /** 申请售后（订单需已发货/已完成） */
    String apply(Long userId, String userName, String orderNo, Integer type, String reason);
    /** 审核：通过则退款+回库存，拒绝则关闭 */
    boolean audit(String aftersaleNo, String handler, boolean pass, String remark);
    CampusAftersale selectByAftersaleNo(String aftersaleNo);
    List<CampusAftersale> selectList(CampusAftersale aftersale);
}
