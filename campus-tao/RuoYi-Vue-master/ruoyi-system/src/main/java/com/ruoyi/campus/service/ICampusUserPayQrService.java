package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusUserPayQr;

/**
 * 用户收款码服务（校园淘 · C2C 直付）
 */
public interface ICampusUserPayQrService {

    /** 查询本人收款码，未上传返回 null */
    CampusUserPayQr getMyPayQr(Long userId);

    /** 保存/覆盖本人收款码（qrUrl 非空校验在服务层完成） */
    void saveMyPayQr(Long userId, String qrUrl);

    /** 查询指定用户（卖家）的收款码，未上传返回 null。供订单直付链路读取 */
    CampusUserPayQr getByUserId(Long userId);
}
