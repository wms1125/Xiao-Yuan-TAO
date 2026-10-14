package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusCart;
import java.util.List;

/**
 * 购物车服务（校园淘）
 */
public interface ICampusCartService {
    /** 加入购物车：已存在则累加数量，否则新增 */
    void addToCart(Long userId, String userName, Long productId, Integer quantity);
    /** 修改数量（仅限本人购物车） */
    int updateQuantity(Long id, Integer quantity, Long userId);
    /** 切换勾选状态（仅限本人购物车） */
    int toggleChecked(Long id, Integer checked, Long userId);
    /** 删除（支持多个，仅限本人购物车） */
    int removeByIds(Long[] ids, Long userId);
    /** 按用户查列表 */
    List<CampusCart> selectListByUserId(Long userId);
    /** 清空已勾选 */
    int cleanChecked(Long userId);
}
