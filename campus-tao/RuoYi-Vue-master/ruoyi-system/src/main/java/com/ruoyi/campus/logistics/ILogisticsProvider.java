package com.ruoyi.campus.logistics;

import java.util.List;

/**
 * 物流轨迹查询 Provider 抽象（校园淘）。
 *
 * 承运商网关可替换：默认实现为通用 HTTP 表单网关，网关地址由环境变量注入。
 * 未配置时 {@link #enabled()} 返回 false，轨迹查询自动降级（返回空，不阻断订单流程）。
 */
public interface ILogisticsProvider {

    /** 是否已配置启用 */
    boolean enabled();

    /** 承运商编码（留痕与展示用），如 SF */
    String carrier();

    /**
     * 按物流单号查询轨迹节点。
     * 网关调用失败时抛出 {@link com.ruoyi.common.exception.ServiceException}，由上层留痕或提示运营。
     */
    List<LogisticsTraceNode> query(String logisticsNo);
}
