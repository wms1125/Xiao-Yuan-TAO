package com.ruoyi.campus.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.dto.CampusAddressSaveDTO;
import com.ruoyi.campus.service.ICampusAddressService;

/**
 * 收货地址 Controller（校园淘）
 * <p>
 * 地址簿是**买家私有数据**：所有接口的用户身份一律取自登录态
 * （`SecurityUtils.getUserId()`），不接受前端传 userId。
 */
@Validated
@RestController
@RequestMapping("/campus/address")
public class CampusAddressController extends BaseController {

    @Autowired
    private ICampusAddressService addressService;

    /** 我的地址列表（默认地址排最前） */
    @PreAuthorize("@ss.hasPermi('campus:address:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(addressService.selectListByUser(SecurityUtils.getUserId()));
    }

    /** 默认地址（无地址时返回 null，下单页据此决定是否提示去新增） */
    @PreAuthorize("@ss.hasPermi('campus:address:list')")
    @GetMapping("/default")
    public AjaxResult defaultAddress() {
        return AjaxResult.success(addressService.selectDefault(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('campus:address:add')")
    @PostMapping
    public AjaxResult add(@Valid @RequestBody CampusAddressSaveDTO body) {
        addressService.addAddress(SecurityUtils.getUserId(), body);
        return AjaxResult.success("地址已保存");
    }

    @PreAuthorize("@ss.hasPermi('campus:address:edit')")
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CampusAddressSaveDTO body) {
        if (body.getAddressId() == null) {
            throw new ServiceException("缺少地址ID");
        }
        addressService.updateAddress(SecurityUtils.getUserId(), body);
        return AjaxResult.success("地址已更新");
    }

    @PreAuthorize("@ss.hasPermi('campus:address:edit')")
    @PutMapping("/default/{addressId}")
    public AjaxResult setDefault(@PathVariable Long addressId) {
        addressService.setDefault(addressId, SecurityUtils.getUserId());
        return AjaxResult.success("已设为默认地址");
    }

    @PreAuthorize("@ss.hasPermi('campus:address:remove')")
    @DeleteMapping("/{addressId}")
    public AjaxResult remove(@PathVariable Long addressId) {
        addressService.deleteAddress(addressId, SecurityUtils.getUserId());
        return AjaxResult.success("地址已删除");
    }
}
