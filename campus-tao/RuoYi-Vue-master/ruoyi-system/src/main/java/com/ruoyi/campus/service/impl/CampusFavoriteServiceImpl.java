package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusFavorite;
import com.ruoyi.campus.mapper.CampusFavoriteMapper;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.ICampusFavoriteService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品收藏服务实现（校园淘）
 *
 * @author campus-tao
 */
@Service
public class CampusFavoriteServiceImpl implements ICampusFavoriteService {

    @Autowired
    private CampusFavoriteMapper favoriteMapper;

    @Autowired
    private CampusProductMapper productMapper;

    @Override
    public List<CampusFavorite> selectListByUser(Long userId) {
        return favoriteMapper.selectListByUser(userId);
    }

    @Override
    public int countByUser(Long userId) {
        return favoriteMapper.countByUserId(userId);
    }

    @Override
    public List<Long> selectFavoredProductIds(Long userId, List<Long> productIds) {
        if (userId == null || productIds == null || productIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return favoriteMapper.selectFavoredProductIds(userId, productIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long userId, Long productId) {
        requireUser(userId);
        requireProduct(productId);
        int existing = favoriteMapper.countByUserAndProduct(userId, productId);
        if (existing > 0) {
            favoriteMapper.deleteByUserAndProduct(userId, productId);
            return false;
        }
        CampusFavorite favorite = new CampusFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favoriteMapper.insertCampusFavorite(favorite);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long userId, Long productId) {
        requireUser(userId);
        return favoriteMapper.deleteByUserAndProduct(userId, productId) > 0;
    }

    @Override
    public int removeByFavoriteId(Long favoriteId, Long userId) {
        requireUser(userId);
        // SQL 已带 user_id：越权时删除 0 行，此处显式抛异常以给出明确反馈
        int rows = favoriteMapper.deleteByFavoriteId(favoriteId, userId);
        if (rows == 0) {
            throw new ServiceException("收藏记录不存在或无权操作");
        }
        return rows;
    }

    // ---- 归属/存在性校验：userId 一律来自登录态，不接受前端传参 ----

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ServiceException("未登录或登录态已失效");
        }
    }

    private void requireProduct(Long productId) {
        if (productId == null || productMapper.selectCampusProductById(productId) == null) {
            throw new ServiceException("商品不存在");
        }
    }
}
