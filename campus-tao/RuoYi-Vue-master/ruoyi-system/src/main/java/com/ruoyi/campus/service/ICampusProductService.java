package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusProduct;
import java.util.List;

/**
 * 二手商品 Service（校园淘）
 */
public interface ICampusProductService {

    /** 发布商品：服务端强制写入卖家身份，sellerId/sellerName 以登录态为准 */
    int insertCampusProduct(CampusProduct product, Long sellerId, String sellerName);

    /** 修改商品：仅卖家本人或管理员可操作 */
    int updateCampusProduct(CampusProduct product, Long userId);

    int deleteCampusProductById(Long id);

    /** 批量删除商品：仅卖家本人或管理员可删除 */
    int deleteCampusProductByIds(Long[] ids, Long userId);

    CampusProduct selectCampusProductById(Long id);

    List<CampusProduct> selectCampusProductList(CampusProduct product);

    /** 上下架：仅卖家本人或管理员可操作 */
    int changeStatus(Long id, Integer status, Long userId);
}
