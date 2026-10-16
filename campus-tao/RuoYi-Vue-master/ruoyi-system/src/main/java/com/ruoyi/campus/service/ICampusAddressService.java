package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusAddress;
import com.ruoyi.campus.dto.CampusAddressSaveDTO;

import java.util.List;

/**
 * 收货地址 Service（校园淘）
 * <p>
 * 所有方法均以 **登录用户** 为作用域：查询只看本人、写操作先校验归属，
 * 不接受前端传入的 userId。
 */
public interface ICampusAddressService {

    /** 本人地址列表（默认地址排最前） */
    List<CampusAddress> selectListByUser(Long userId);

    /** 取本人地址，非本人或不存在则抛业务异常 */
    CampusAddress requireOwned(Long addressId, Long userId);

    /** 本人默认地址，无则返回 null */
    CampusAddress selectDefault(Long userId);

    /** 新增地址，返回受影响行数 */
    int addAddress(Long userId, CampusAddressSaveDTO dto);

    /** 修改地址（含归属校验） */
    int updateAddress(Long userId, CampusAddressSaveDTO dto);

    /** 删除地址（归属校验在 SQL 层同时兜底） */
    int deleteAddress(Long addressId, Long userId);

    /** 设为默认地址（同时清除该用户其余默认标记） */
    int setDefault(Long addressId, Long userId);
}
