package com.ruoyi.campus.service;

import java.util.List;

import com.ruoyi.campus.domain.CampusLogisticsTrace;

/**
 * 物流轨迹服务（校园淘）
 */
public interface ICampusLogisticsService {

    /** 物流轨迹 Provider 是否已配置启用（未启用时轨迹查询自动降级为空） */
    boolean providerEnabled();

    /** 查询订单的物流轨迹（调用方须先完成订单归属校验） */
    List<CampusLogisticsTrace> listTrace(String orderNo);

    /**
     * 同步单个订单的物流轨迹，返回新增节点数（重复节点幂等忽略）。
     * 订单无物流单号或 Provider 未启用时返回 0；网关异常向上抛出以便运营感知。
     */
    int syncTrace(String orderNo);

    /** 批量同步已发货订单的物流轨迹，返回新增节点总数；单笔失败不影响其余订单 */
    int syncShippedOrders(int limit);
}
