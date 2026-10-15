package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
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

    /** 列表页概览：按状态分组统计售后单数与退款金额（与 selectList 使用同一筛选条件） */
    List<CampusStatusStat> countGroupByStatus(CampusAftersale aftersale);
}
