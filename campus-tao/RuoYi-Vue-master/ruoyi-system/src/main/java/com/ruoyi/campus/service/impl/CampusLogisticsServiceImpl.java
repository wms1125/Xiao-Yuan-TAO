package com.ruoyi.campus.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.campus.domain.CampusLogisticsTrace;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.logistics.ILogisticsProvider;
import com.ruoyi.campus.logistics.LogisticsTraceNode;
import com.ruoyi.campus.mapper.CampusLogisticsTraceMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.service.ICampusLogisticsService;
import com.ruoyi.common.utils.StringUtils;

/**
 * 物流轨迹服务实现（校园淘）。
 *
 * 查询链路：订单 → 物流单号 → Provider 网关 → 轨迹节点落库（幂等）。
 * Provider 未配置时整体降级：查询返回空、同步返回 0，不阻断订单流程。
 */
@Service
public class CampusLogisticsServiceImpl implements ICampusLogisticsService {

    private static final Logger log = LoggerFactory.getLogger(CampusLogisticsServiceImpl.class);

    /** 批量同步默认条数上限 */
    private static final int DEFAULT_SYNC_LIMIT = 200;

    @Autowired
    private CampusLogisticsTraceMapper traceMapper;

    @Autowired
    private CampusOrderMapper orderMapper;

    @Autowired
    private ILogisticsProvider logisticsProvider;

    @Override
    public boolean providerEnabled() {
        return logisticsProvider.enabled();
    }

    @Override
    public List<CampusLogisticsTrace> listTrace(String orderNo) {
        if (StringUtils.isBlank(orderNo)) {
            return new ArrayList<>();
        }
        return traceMapper.selectByOrderNo(orderNo);
    }

    @Override
    public int syncTrace(String orderNo) {
        return syncOrder(orderMapper.selectOrderByOrderNo(orderNo));
    }

    @Override
    public int syncShippedOrders(int limit) {
        if (!logisticsProvider.enabled()) {
            return 0;
        }
        List<CampusOrder> orders = orderMapper.selectShippedOrders(limit <= 0 ? DEFAULT_SYNC_LIMIT : limit);
        int total = 0;
        for (CampusOrder order : orders) {
            try {
                total += syncOrder(order);
            } catch (Exception e) {
                // 单笔订单失败不阻断其余订单的同步
                log.warn("物流轨迹批量同步：订单 {} 同步失败，已跳过", order.getOrderNo(), e);
            }
        }
        return total;
    }

    private int syncOrder(CampusOrder order) {
        if (order == null || StringUtils.isBlank(order.getLogisticsNo())) {
            return 0;
        }
        if (!logisticsProvider.enabled()) {
            return 0;
        }
        List<LogisticsTraceNode> nodes = logisticsProvider.query(order.getLogisticsNo());
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        List<CampusLogisticsTrace> rows = new ArrayList<>(nodes.size());
        for (LogisticsTraceNode node : nodes) {
            CampusLogisticsTrace trace = new CampusLogisticsTrace();
            trace.setOrderNo(order.getOrderNo());
            trace.setLogisticsNo(order.getLogisticsNo());
            trace.setCarrier(node.getCarrier());
            trace.setStatusLabel(node.getStatusLabel());
            trace.setContext(node.getContext());
            trace.setTraceTime(node.getTraceTime());
            rows.add(trace);
        }
        return traceMapper.insertIgnoreBatch(rows);
    }
}
