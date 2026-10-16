package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusAddress;
import com.ruoyi.campus.dto.CampusAddressSaveDTO;
import com.ruoyi.campus.mapper.CampusAddressMapper;
import com.ruoyi.campus.service.impl.CampusAddressServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：收货地址（归属校验 / 默认地址唯一性 / 数量上限 / 空态回落）。
 *
 * <p>重点不是"CRUD 能跑通"，而是三条**安全与一致性**约束：
 * <ol>
 *   <li>越权：读写他人地址必须被拒（归属校验 + SQL 层 user_id 条件双保险）；</li>
 *   <li>默认唯一：设默认前必须清掉该用户其余默认标记，否则会出现多个默认地址；</li>
 *   <li>上限：地址簿不能被无限刷入。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 收货地址 Service")
class CampusAddressServiceTest {

    private static final long USER_ID = 2L;
    private static final long OTHER_USER_ID = 9L;
    private static final long ADDRESS_ID = 100L;

    @Mock
    private CampusAddressMapper addressMapper;

    @InjectMocks
    private CampusAddressServiceImpl addressService;

    @Captor
    private ArgumentCaptor<CampusAddress> addressCaptor;

    private CampusAddressSaveDTO dto(String receiver, String phone, String region, String detail, Integer isDefault) {
        CampusAddressSaveDTO dto = new CampusAddressSaveDTO();
        dto.setReceiver(receiver);
        dto.setPhone(phone);
        dto.setRegion(region);
        dto.setDetail(detail);
        dto.setIsDefault(isDefault);
        return dto;
    }

    private CampusAddress address(long addressId, long userId, Integer isDefault) {
        CampusAddress a = new CampusAddress();
        a.setAddressId(addressId);
        a.setUserId(userId);
        a.setReceiver("张三");
        a.setPhone("13800000000");
        a.setRegion("东校区");
        a.setDetail("1号楼101");
        a.setIsDefault(isDefault);
        return a;
    }

    // ---------------- 归属校验 ----------------

    @Test
    @DisplayName("requireOwned：他人地址被拒（越权读）")
    void requireOwnedRejectsOtherUser() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, OTHER_USER_ID, 0));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> addressService.requireOwned(ADDRESS_ID, USER_ID));

        assertEquals("收货地址不存在或无权操作", ex.getMessage());
    }

    @Test
    @DisplayName("requireOwned：地址不存在被拒")
    void requireOwnedRejectsMissing() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> addressService.requireOwned(ADDRESS_ID, USER_ID));
    }

    @Test
    @DisplayName("requireOwned：addressId 为 null 直接拒绝（不做无谓查询）")
    void requireOwnedRejectsNullId() {
        assertThrows(ServiceException.class, () -> addressService.requireOwned(null, USER_ID));

        verify(addressMapper, never()).selectAddressById(any());
    }

    @Test
    @DisplayName("requireOwned：本人地址正常返回")
    void requireOwnedAcceptsOwner() {
        CampusAddress mine = address(ADDRESS_ID, USER_ID, 0);
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(mine);

        assertSame(mine, addressService.requireOwned(ADDRESS_ID, USER_ID));
    }

    // ---------------- 新增 ----------------

    @Test
    @DisplayName("新增：第一条地址强制为默认（否则下单时无可选默认地址）")
    void addFirstAddressForcedDefault() {
        when(addressMapper.countByUserId(USER_ID)).thenReturn(0);

        addressService.addAddress(USER_ID, dto("张三", "13800000000", "东校区", "1号楼101", 0));

        verify(addressMapper).insertCampusAddress(addressCaptor.capture());
        assertEquals(Integer.valueOf(1), addressCaptor.getValue().getIsDefault());
        assertEquals(USER_ID, addressCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("新增：勾选默认时先清掉该用户其余默认标记")
    void addDefaultClearsPreviousDefault() {
        when(addressMapper.countByUserId(USER_ID)).thenReturn(3);

        addressService.addAddress(USER_ID, dto("张三", "13800000000", "东校区", "1号楼101", 1));

        verify(addressMapper).clearDefaultByUser(USER_ID);
        verify(addressMapper).insertCampusAddress(addressCaptor.capture());
        assertEquals(Integer.valueOf(1), addressCaptor.getValue().getIsDefault());
    }

    @Test
    @DisplayName("新增：非首条且不勾选默认时，不清默认标记")
    void addNonDefaultKeepsExistingDefault() {
        when(addressMapper.countByUserId(USER_ID)).thenReturn(3);

        addressService.addAddress(USER_ID, dto("李四", "13900000000", "西校区", "2号楼202", 0));

        verify(addressMapper, never()).clearDefaultByUser(anyLong());
        verify(addressMapper).insertCampusAddress(addressCaptor.capture());
        assertEquals(Integer.valueOf(0), addressCaptor.getValue().getIsDefault());
    }

    @Test
    @DisplayName("新增：超过 20 条上限时拒绝")
    void addRejectsWhenLimitReached() {
        when(addressMapper.countByUserId(USER_ID)).thenReturn(20);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> addressService.addAddress(USER_ID, dto("张三", "13800000000", "东校区", "1号楼101", 0)));

        assertTrue(ex.getMessage().contains("上限"), ex.getMessage());
        verify(addressMapper, never()).insertCampusAddress(any());
    }

    @Test
    @DisplayName("新增：region 缺省时落空串而非 null（避免拼地址出现 null）")
    void addNullRegionBecomesEmptyString() {
        when(addressMapper.countByUserId(USER_ID)).thenReturn(1);

        addressService.addAddress(USER_ID, dto("张三", "13800000000", null, "1号楼101", 0));

        verify(addressMapper).insertCampusAddress(addressCaptor.capture());
        assertEquals("", addressCaptor.getValue().getRegion());
        assertEquals("1号楼101", addressCaptor.getValue().fullAddress());
    }

    // ---------------- 修改 ----------------

    @Test
    @DisplayName("修改：越权改他人地址被拒，且不落库")
    void updateRejectsOtherUser() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, OTHER_USER_ID, 0));

        CampusAddressSaveDTO body = dto("张三", "13800000000", "东校区", "1号楼101", 0);
        body.setAddressId(ADDRESS_ID);

        assertThrows(ServiceException.class, () -> addressService.updateAddress(USER_ID, body));
        verify(addressMapper, never()).updateCampusAddress(any());
    }

    @Test
    @DisplayName("修改：勾选默认时先清默认再置位")
    void updateToDefaultClearsPrevious() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, USER_ID, 0));

        CampusAddressSaveDTO body = dto("张三", "13800000000", "东校区", "1号楼101", 1);
        body.setAddressId(ADDRESS_ID);

        addressService.updateAddress(USER_ID, body);

        verify(addressMapper).clearDefaultByUser(USER_ID);
        verify(addressMapper).updateCampusAddress(addressCaptor.capture());
        assertEquals(Integer.valueOf(1), addressCaptor.getValue().getIsDefault());
        assertEquals(ADDRESS_ID, addressCaptor.getValue().getAddressId());
    }

    @Test
    @DisplayName("修改：不勾选默认时不触碰默认标记（isDefault 传 null 表示不修改）")
    void updateWithoutDefaultFlagLeavesItAlone() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, USER_ID, 1));

        CampusAddressSaveDTO body = dto("张三改名", "13800000000", "东校区", "1号楼101", null);
        body.setAddressId(ADDRESS_ID);

        addressService.updateAddress(USER_ID, body);

        verify(addressMapper, never()).clearDefaultByUser(anyLong());
        verify(addressMapper).updateCampusAddress(addressCaptor.capture());
        assertNull(addressCaptor.getValue().getIsDefault());
    }

    // ---------------- 删除 / 设默认 ----------------

    @Test
    @DisplayName("删除：越权删除被拒，且不执行删除语句")
    void deleteRejectsOtherUser() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, OTHER_USER_ID, 0));

        assertThrows(ServiceException.class, () -> addressService.deleteAddress(ADDRESS_ID, USER_ID));
        verify(addressMapper, never()).deleteCampusAddressById(any(), any());
    }

    @Test
    @DisplayName("删除：本人地址删除时把 userId 一并下发（SQL 层再兜一次越权）")
    void deletePassesUserIdToSql() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, USER_ID, 0));
        when(addressMapper.deleteCampusAddressById(ADDRESS_ID, USER_ID)).thenReturn(1);

        assertEquals(1, addressService.deleteAddress(ADDRESS_ID, USER_ID));
        verify(addressMapper).deleteCampusAddressById(eq(ADDRESS_ID), eq(USER_ID));
    }

    @Test
    @DisplayName("设默认：先清默认再置位，且归属校验先行")
    void setDefaultClearsThenSets() {
        when(addressMapper.selectAddressById(ADDRESS_ID)).thenReturn(address(ADDRESS_ID, USER_ID, 0));

        addressService.setDefault(ADDRESS_ID, USER_ID);

        verify(addressMapper).clearDefaultByUser(USER_ID);
        verify(addressMapper).updateCampusAddress(addressCaptor.capture());
        assertEquals(Integer.valueOf(1), addressCaptor.getValue().getIsDefault());
        assertEquals(USER_ID, addressCaptor.getValue().getUserId());
        assertEquals(ADDRESS_ID, addressCaptor.getValue().getAddressId());
    }

    // ---------------- 默认地址解析 ----------------

    @Test
    @DisplayName("selectDefault：返回被标记为默认的那条")
    void selectDefaultReturnsFlagged() {
        CampusAddress normal = address(1L, USER_ID, 0);
        CampusAddress def = address(2L, USER_ID, 1);
        when(addressMapper.selectListByUser(USER_ID)).thenReturn(Arrays.asList(normal, def));

        assertSame(def, addressService.selectDefault(USER_ID));
    }

    @Test
    @DisplayName("selectDefault：无显式默认时回落到列表首条（避免下单页空着）")
    void selectDefaultFallsBackToFirst() {
        CampusAddress first = address(1L, USER_ID, 0);
        when(addressMapper.selectListByUser(USER_ID)).thenReturn(Arrays.asList(first, address(2L, USER_ID, 0)));

        assertSame(first, addressService.selectDefault(USER_ID));
    }

    @Test
    @DisplayName("selectDefault：一条地址都没有时返回 null")
    void selectDefaultReturnsNullWhenEmpty() {
        when(addressMapper.selectListByUser(USER_ID)).thenReturn(Collections.emptyList());

        assertNull(addressService.selectDefault(USER_ID));
    }

    @Test
    @DisplayName("列表：透传 mapper 结果（排序由 SQL 负责：默认优先 + 创建时间倒序）")
    void selectListDelegatesToMapper() {
        when(addressMapper.selectListByUser(USER_ID)).thenReturn(Collections.singletonList(address(1L, USER_ID, 1)));

        assertEquals(1, addressService.selectListByUser(USER_ID).size());
        verify(addressMapper, times(1)).selectListByUser(USER_ID);
    }
}
