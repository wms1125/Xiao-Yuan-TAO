package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收货地址 Mapper（校园淘）
 */
@Mapper
public interface CampusAddressMapper {
    int insertCampusAddress(CampusAddress address);

    int updateCampusAddress(CampusAddress address);

    int deleteCampusAddressById(@Param("addressId") Long addressId, @Param("userId") Long userId);

    /** 把该用户所有地址置为非默认（新增/修改默认地址前调用） */
    int clearDefaultByUser(@Param("userId") Long userId);

    CampusAddress selectAddressById(Long addressId);

    List<CampusAddress> selectListByUser(Long userId);

    int countByUserId(Long userId);
}
