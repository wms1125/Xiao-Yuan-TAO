package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusAddress;
import com.ruoyi.campus.dto.CampusAddressSaveDTO;
import com.ruoyi.campus.mapper.CampusAddressMapper;
import com.ruoyi.campus.service.ICampusAddressService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampusAddressServiceImpl implements ICampusAddressService {

    /** 单个用户地址数量上限：防止地址簿被无限刷入 */
    private static final int MAX_ADDRESSES_PER_USER = 20;

    @Autowired
    private CampusAddressMapper addressMapper;

    @Override
    public List<CampusAddress> selectListByUser(Long userId) {
        return addressMapper.selectListByUser(userId);
    }

    @Override
    public CampusAddress requireOwned(Long addressId, Long userId) {
        CampusAddress address = addressId == null ? null : addressMapper.selectAddressById(addressId);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new ServiceException("收货地址不存在或无权操作");
        }
        return address;
    }

    @Override
    public CampusAddress selectDefault(Long userId) {
        List<CampusAddress> list = addressMapper.selectListByUser(userId);
        for (CampusAddress a : list) {
            if (Integer.valueOf(1).equals(a.getIsDefault())) {
                return a;
            }
        }
        // 没有显式默认地址时回落到最新一条，避免下单页空着
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional
    public int addAddress(Long userId, CampusAddressSaveDTO dto) {
        int existing = addressMapper.countByUserId(userId);
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new ServiceException("收货地址数量已达上限（" + MAX_ADDRESSES_PER_USER + " 条）");
        }
        CampusAddress address = toDomain(userId, dto);
        // 第一条地址无条件设为默认，否则用户下单时会没有可选默认地址
        boolean wantDefault = Integer.valueOf(1).equals(dto.getIsDefault()) || existing == 0;
        if (wantDefault) {
            addressMapper.clearDefaultByUser(userId);
        }
        address.setIsDefault(wantDefault ? 1 : 0);
        return addressMapper.insertCampusAddress(address);
    }

    @Override
    @Transactional
    public int updateAddress(Long userId, CampusAddressSaveDTO dto) {
        requireOwned(dto.getAddressId(), userId);
        CampusAddress address = toDomain(userId, dto);
        address.setAddressId(dto.getAddressId());
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            addressMapper.clearDefaultByUser(userId);
            address.setIsDefault(1);
        }
        return addressMapper.updateCampusAddress(address);
    }

    @Override
    public int deleteAddress(Long addressId, Long userId) {
        requireOwned(addressId, userId);
        return addressMapper.deleteCampusAddressById(addressId, userId);
    }

    @Override
    @Transactional
    public int setDefault(Long addressId, Long userId) {
        requireOwned(addressId, userId);
        addressMapper.clearDefaultByUser(userId);
        CampusAddress address = new CampusAddress();
        address.setAddressId(addressId);
        address.setUserId(userId);
        address.setIsDefault(1);
        return addressMapper.updateCampusAddress(address);
    }

    private CampusAddress toDomain(Long userId, CampusAddressSaveDTO dto) {
        CampusAddress address = new CampusAddress();
        address.setUserId(userId);
        address.setReceiver(dto.getReceiver());
        address.setPhone(dto.getPhone());
        address.setRegion(dto.getRegion() == null ? "" : dto.getRegion());
        address.setDetail(dto.getDetail());
        address.setIsDefault(dto.getIsDefault());
        return address;
    }
}
