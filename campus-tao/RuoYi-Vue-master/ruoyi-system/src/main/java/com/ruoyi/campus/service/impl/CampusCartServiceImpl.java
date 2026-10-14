package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusCart;
import com.ruoyi.campus.mapper.CampusCartMapper;
import com.ruoyi.campus.service.ICampusCartService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampusCartServiceImpl implements ICampusCartService {

    @Autowired
    private CampusCartMapper cartMapper;

    @Override
    @Transactional
    public void addToCart(Long userId, String userName, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            quantity = 1;
        }
        CampusCart exist = cartMapper.selectByUserAndProduct(userId, productId);
        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + quantity);
            cartMapper.updateCampusCart(exist);
        } else {
            CampusCart cart = new CampusCart();
            cart.setUserId(userId);
            cart.setUserName(userName);
            cart.setProductId(productId);
            cart.setQuantity(quantity);
            cart.setChecked(1);
            cartMapper.insertCampusCart(cart);
        }
    }

    @Override
    public int updateQuantity(Long id, Integer quantity, Long userId) {
        CampusCart cart = requireOwnedCart(id, userId);
        cart.setQuantity(quantity);
        return cartMapper.updateCampusCart(cart);
    }

    @Override
    public int toggleChecked(Long id, Integer checked, Long userId) {
        CampusCart cart = requireOwnedCart(id, userId);
        cart.setChecked(checked);
        return cartMapper.updateCampusCart(cart);
    }

    @Override
    public int removeByIds(Long[] ids, Long userId) {
        int n = 0;
        if (ids != null) {
            for (Long id : ids) {
                CampusCart cart = cartMapper.selectCartById(id);
                // 仅允许删除本人购物车记录，防止越权删除他人数据
                if (cart != null && userId.equals(cart.getUserId())) {
                    n += cartMapper.deleteCampusCartById(id);
                }
            }
        }
        return n;
    }

    /** 校验购物车记录归属，非本人操作直接拒绝 */
    private CampusCart requireOwnedCart(Long id, Long userId) {
        CampusCart cart = cartMapper.selectCartById(id);
        if (cart == null || !userId.equals(cart.getUserId())) {
            throw new ServiceException("购物车记录不存在或无权操作");
        }
        return cart;
    }

    @Override
    public List<CampusCart> selectListByUserId(Long userId) {
        CampusCart param = new CampusCart();
        param.setUserId(userId);
        return cartMapper.selectCartList(param);
    }

    @Override
    public int cleanChecked(Long userId) {
        List<CampusCart> list = cartMapper.selectCartList(new CampusCart() {{
            setUserId(userId);
            setChecked(1);
        }});
        int n = 0;
        for (CampusCart c : list) {
            n += cartMapper.deleteCampusCartById(c.getId());
        }
        return n;
    }
}
