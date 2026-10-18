package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusUserPayQr;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户收款码 Mapper（校园淘 · C2C 直付）
 */
@Mapper
public interface CampusUserPayQrMapper {

    /** 按用户查收款码，未上传返回 null */
    CampusUserPayQr selectByUserId(@Param("userId") Long userId);

    /**
     * 保存（存在则覆盖）：一条 upsert，避免先查后插的并发窗口。
     * 主键 user_id 冲突时更新 qr_url。
     */
    int upsert(@Param("userId") Long userId, @Param("qrUrl") String qrUrl);
}
