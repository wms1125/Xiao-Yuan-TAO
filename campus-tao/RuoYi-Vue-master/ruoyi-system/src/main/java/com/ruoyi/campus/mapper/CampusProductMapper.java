package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusProduct;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 二手商品 Mapper（校园淘）
 */
@Mapper
public interface CampusProductMapper {
    int insertCampusProduct(CampusProduct product);

    int updateCampusProduct(CampusProduct product);

    int deleteCampusProductById(Long id);

    CampusProduct selectCampusProductById(Long id);

    List<CampusProduct> selectCampusProductList(CampusProduct product);

    /** 扣减库存：WHERE stock >= count，返回影响行数（0=库存不足） */
    int decreaseStock(Long id, int count);

    /** 回补库存 */
    int increaseStock(Long id, int count);
}
