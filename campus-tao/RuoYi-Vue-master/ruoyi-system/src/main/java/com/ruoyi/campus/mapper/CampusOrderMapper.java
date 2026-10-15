package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 订单 Mapper（校园淘）
 */
@Mapper
public interface CampusOrderMapper {
    int insertCampusOrder(CampusOrder order);
    int updateCampusOrder(CampusOrder order);
    CampusOrder selectOrderByOrderNo(String orderNo);
    CampusOrder selectOrderById(Long id);
    List<CampusOrder> selectOrderList(CampusOrder order);
    int countByUserId(@Param("userId") Long userId);

    /** 按状态分组统计订单数（与 selectOrderList 使用同一筛选条件），供列表页概览使用 */
    List<CampusStatusStat> countGroupByStatus(CampusOrder order);

    /** 查询超时未支付订单（status=0 且 create_time 早于 deadline），供定时任务自动取消 */
    List<CampusOrder> selectTimeoutWaitPayOrders(@Param("deadline") Date deadline);

    /** 查询已发货且有物流单号的订单，供定时任务同步物流轨迹 */
    List<CampusOrder> selectShippedOrders(@Param("limit") Integer limit);

    /**
     * 原子取消：仅当订单仍为待付款(0)时才改为已取消(4)，返回影响行数。
     * 影响行数=1 表示本次取消生效（可安全回补库存）；0 表示已被其他动作处理，跳过。
     */
    int cancelWaitPayOrderIfStillWaitPay(@Param("orderNo") String orderNo, @Param("cancelTime") Date cancelTime);
}
