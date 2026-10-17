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

import static com.ruoyi.campus.service.ICampusFavoriteService.MAX_FAVORITES;

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
        // 已到上限时先给明确提示：这条路径覆盖绝大多数情况，
        // 不必等 INSERT 失败再回头查一遍。
        if (favoriteMapper.countByUserId(userId) >= MAX_FAVORITES) {
            throw new ServiceException(limitMessage());
        }
        // 竞态兜底：并发下 count 已通过但插入时又满了，insertIfUnderLimit 返回 0 行。
        // 不能靠"再查一次 count"来判断——那时别人可能刚好取消了一条，count 又降下来，
        // 会把真正的"已超限"误判成别的错误。
        int inserted = favoriteMapper.insertIfUnderLimit(userId, productId, MAX_FAVORITES);
        if (inserted == 0) {
            throw new ServiceException(limitMessage());
        }
        return true;
    }

    /** 超限提示必须说明上限值和下一步动作，否则用户不知道该干什么 */
    private String limitMessage() {
        return "收藏数量已达上限（最多 " + MAX_FAVORITES + " 个），请先取消部分收藏后再试";
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
