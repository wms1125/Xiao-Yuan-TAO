package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusUserPayQr;
import com.ruoyi.campus.mapper.CampusUserPayQrMapper;
import com.ruoyi.campus.service.ICampusUserPayQrService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CampusUserPayQrServiceImpl implements ICampusUserPayQrService {

    @Autowired
    private CampusUserPayQrMapper payQrMapper;

    @Override
    public CampusUserPayQr getMyPayQr(Long userId) {
        return payQrMapper.selectByUserId(userId);
    }

    @Override
    public void saveMyPayQr(Long userId, String qrUrl) {
        if (qrUrl == null || qrUrl.trim().isEmpty()) {
            throw new ServiceException("收款码图片不能为空");
        }
        String trimmed = qrUrl.trim();
        // 只接受本站上传通道返回的相对路径，拒绝外链与脚本注入
        if (!trimmed.startsWith("/profile/") && !trimmed.startsWith("http")) {
            throw new ServiceException("收款码地址不合法，请通过系统上传后保存");
        }
        if (trimmed.length() > 500) {
            throw new ServiceException("收款码地址过长");
        }
        payQrMapper.upsert(userId, trimmed);
    }

    @Override
    public CampusUserPayQr getByUserId(Long userId) {
        return payQrMapper.selectByUserId(userId);
    }
}
