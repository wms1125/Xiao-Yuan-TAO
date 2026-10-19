package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusPaymentRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 支付流水 Mapper（校园淘）
 */
@Mapper
public interface CampusPaymentRecordMapper {

    /** 新增一条支付/退款流水 */
    int insertCampusPaymentRecord(CampusPaymentRecord record);

    /** 按订单号查询流水（对账用），按时间正序 */
    List<CampusPaymentRecord> selectByOrderNo(String orderNo);
}
