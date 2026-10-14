package com.ruoyi.campus.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.mapper.CampusProductMapper;
import com.ruoyi.campus.service.ICampusProductService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

@Service
public class CampusProductServiceImpl implements ICampusProductService {
    @Autowired
    private CampusProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertCampusProduct(CampusProduct product, Long sellerId, String sellerName) {
        // 服务端强制卖家身份：覆盖前端传入值，防止伪造他人名义发布
        product.setSellerId(sellerId);
        product.setSellerName(sellerName);
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        return productMapper.insertCampusProduct(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCampusProduct(CampusProduct product, Long userId) {
        // 仅商品归属人或管理员可修改，防止越权改动他人商品
        assertCanManage(product.getId(), userId);
        return productMapper.updateCampusProduct(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCampusProductById(Long id) {
        return productMapper.deleteCampusProductById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCampusProductByIds(Long[] ids, Long userId) {
        int rows = 0;
        if (ids != null) {
            for (Long id : ids) {
                // 仅删除本人（或管理员有权管理）的商品
                if (canManage(id, userId)) {
                    rows += productMapper.deleteCampusProductById(id);
                }
            }
        }
        return rows;
    }

    @Override
    public CampusProduct selectCampusProductById(Long id) {
        return productMapper.selectCampusProductById(id);
    }

    @Override
    public List<CampusProduct> selectCampusProductList(CampusProduct product) {
        return productMapper.selectCampusProductList(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long id, Integer status, Long userId) {
        assertCanManage(id, userId);
        CampusProduct product = new CampusProduct();
        product.setId(id);
        product.setStatus(status);
        return productMapper.updateCampusProduct(product);
    }

    /** 判断当前用户是否有权管理该商品（卖家本人或管理员） */
    private boolean canManage(Long id, Long userId) {
        if (id == null || userId == null) {
            return false;
        }
        CampusProduct exist = productMapper.selectCampusProductById(id);
        return exist != null && (SecurityUtils.isAdmin(userId) || userId.equals(exist.getSellerId()));
    }

    private void assertCanManage(Long id, Long userId) {
        if (!canManage(id, userId)) {
            throw new ServiceException("商品不存在或无权操作");
        }
    }
}
