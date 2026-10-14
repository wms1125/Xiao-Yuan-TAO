package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusReview;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.mapper.CampusReviewMapper;
import com.ruoyi.campus.service.ICampusReviewService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampusReviewServiceImpl implements ICampusReviewService {

    @Autowired
    private CampusReviewMapper reviewMapper;
    @Autowired
    private CampusOrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addReview(Long userId, String userName, Long orderId, Long productId,
                          Integer rating, String content, String images) {
        CampusOrder order = orderMapper.selectOrderById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() != 3) {
            throw new ServiceException("订单未完成，无法评价");
        }
        if (reviewMapper.selectByOrderAndProduct(orderId, productId) != null) {
            throw new ServiceException("该商品已评价");
        }
        CampusReview r = new CampusReview();
        r.setOrderId(orderId);
        r.setProductId(productId);
        r.setUserId(userId);
        r.setUserName(userName);
        r.setRating(rating == null ? 5 : rating);
        r.setContent(content);
        r.setImages(images);
        reviewMapper.insertCampusReview(r);
    }

    @Override
    public List<CampusReview> selectByProductId(Long productId) {
        return reviewMapper.selectByProductId(productId);
    }

    @Override
    public CampusReview selectByOrderAndProduct(Long orderId, Long productId) {
        return reviewMapper.selectByOrderAndProduct(orderId, productId);
    }
}
