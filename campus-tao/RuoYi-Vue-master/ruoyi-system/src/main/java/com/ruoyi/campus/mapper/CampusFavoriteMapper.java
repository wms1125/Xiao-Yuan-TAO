package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusFavorite;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品收藏 Mapper（校园淘）
 *
 * @author campus-tao
 */
public interface CampusFavoriteMapper {

    /**
     * 收藏列表（联表带出商品概要）。
     *
     * <p>按收藏时间倒序，最新收藏在最前。</p>
     */
    List<CampusFavorite> selectListByUser(@Param("userId") Long userId);

    /** 收藏总数 */
    int countByUserId(@Param("userId") Long userId);

    /** 判断某商品是否已被该用户收藏 */
    int countByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /** 批量判断商品收藏状态（商品卡片列表用，避免 N+1） */
    List<Long> selectFavoredProductIds(@Param("userId") Long userId, @Param("productIds") List<Long> productIds);

    /**
     * 新增收藏。依赖 {@code uk_user_product} 唯一约束防重，
     * 重复插入会抛 {@code DuplicateKeyException}——故服务层必须先判断再插入。
     */
    int insertCampusFavorite(CampusFavorite favorite);

    /**
     * 取消收藏。<b>SQL 自带 user_id 条件</b>：
     * 越权删除在 SQL 层即被拦住（返回 0 行），不依赖上层判空。
     */
    int deleteByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /** 按收藏 ID 删除（同样带 user_id 条件） */
    int deleteByFavoriteId(@Param("favoriteId") Long favoriteId, @Param("userId") Long userId);
}
