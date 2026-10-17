package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusFavorite;

import java.util.List;

/**
 * 商品收藏服务（校园淘）
 *
 * @author campus-tao
 */
public interface ICampusFavoriteService {

    /** 收藏列表（含商品概要，最新在前） */
    List<CampusFavorite> selectListByUser(Long userId);

    /** 收藏总数 */
    int countByUser(Long userId);

    /** 批量判断收藏状态，返回已收藏的商品 ID 集合 */
    List<Long> selectFavoredProductIds(Long userId, List<Long> productIds);

    /**
     * 切换收藏状态：未收藏则收藏，已收藏则取消。
     *
     * <p>返回 {@code true} 表示当前为"已收藏"，{@code false} 表示"已取消"。</p>
     */
    boolean toggle(Long userId, Long productId);

    /** 取消收藏（按商品 ID）。已收藏时返回 true，本来就没收藏时返回 false。 */
    boolean remove(Long userId, Long productId);

    /** 取消收藏（按收藏 ID），越权时抛异常 */
    int removeByFavoriteId(Long favoriteId, Long userId);

    /**
     * 单个用户收藏数量上限。
     *
     * <p>为什么要有上限：收藏是买家私有数据且无成本，早期没有限制时
     * 脚本可以无限收藏，把别人的收藏列表接口拖垮，也占满磁盘。
     * 200 个对真实用户绰绰有余（商品列表通常也就几十个），
     * 却能挡住"批量灌收藏"这类滥用。</p>
     */
    int MAX_FAVORITES = 200;
}
