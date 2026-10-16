package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.controller.CampusAddressController;
import com.ruoyi.campus.dto.CampusAddressSaveDTO;
import com.ruoyi.campus.service.ICampusAddressService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;

/**
 * Controller 层测试：收货地址接口。
 *
 * <p>核心断言是**身份来源**：所有接口的 userId 必须取自登录态，
 * 而不是请求体里的任何字段——地址簿是买家私有数据，前端传什么都不该影响归属。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 收货地址 Controller")
class CampusControllerAddressTest {

    private static final long STUDENT_ID = 2L;
    private static final String STUDENT_NAME = "ry";
    private static final long ADDRESS_ID = 100L;

    @Mock
    private ICampusAddressService addressService;

    @InjectMocks
    private CampusAddressController addressController;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, STUDENT_NAME);
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    private CampusAddressSaveDTO body() {
        CampusAddressSaveDTO body = new CampusAddressSaveDTO();
        body.setReceiver("张三");
        body.setPhone("13800000000");
        body.setRegion("东校区");
        body.setDetail("1号楼101");
        body.setIsDefault(0);
        return body;
    }

    @Test
    @DisplayName("列表：以登录用户为作用域查询")
    void listUsesLoginUser() {
        AjaxResult result = addressController.list();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(addressService).selectListByUser(STUDENT_ID);
    }

    @Test
    @DisplayName("默认地址：以登录用户为作用域查询")
    void defaultAddressUsesLoginUser() {
        addressController.defaultAddress();

        verify(addressService).selectDefault(STUDENT_ID);
    }

    @Test
    @DisplayName("新增：userId 取自登录态（前端无法指定归属）")
    void addUsesLoginUser() {
        CampusAddressSaveDTO body = body();

        AjaxResult result = addressController.add(body);

        assertEquals("地址已保存", result.get(AjaxResult.MSG_TAG));
        verify(addressService).addAddress(eq(STUDENT_ID), eq(body));
    }

    @Test
    @DisplayName("修改：缺少 addressId 时直接拒绝，不进入 service")
    void editWithoutIdRejected() {
        CampusAddressSaveDTO body = body();

        ServiceException ex = assertThrows(ServiceException.class, () -> addressController.edit(body));

        assertEquals("缺少地址ID", ex.getMessage());
        verify(addressService, never()).updateAddress(any(), any());
    }

    @Test
    @DisplayName("修改：带 addressId 时以登录用户身份提交")
    void editUsesLoginUser() {
        CampusAddressSaveDTO body = body();
        body.setAddressId(ADDRESS_ID);

        AjaxResult result = addressController.edit(body);

        assertEquals("地址已更新", result.get(AjaxResult.MSG_TAG));
        verify(addressService).updateAddress(eq(STUDENT_ID), eq(body));
    }

    @Test
    @DisplayName("设为默认：以登录用户身份操作")
    void setDefaultUsesLoginUser() {
        AjaxResult result = addressController.setDefault(ADDRESS_ID);

        assertEquals("已设为默认地址", result.get(AjaxResult.MSG_TAG));
        verify(addressService).setDefault(ADDRESS_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("删除：以登录用户身份操作")
    void removeUsesLoginUser() {
        AjaxResult result = addressController.remove(ADDRESS_ID);

        assertEquals("地址已删除", result.get(AjaxResult.MSG_TAG));
        verify(addressService).deleteAddress(ADDRESS_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("新增：service 抛出的业务异常原样上抛（由全局处理器转成统一响应）")
    void addPropagatesServiceException() {
        CampusAddressSaveDTO body = body();
        when(addressService.addAddress(any(), any())).thenThrow(new ServiceException("收货地址数量已达上限（20 条）"));

        assertThrows(ServiceException.class, () -> addressController.add(body));
    }
}
