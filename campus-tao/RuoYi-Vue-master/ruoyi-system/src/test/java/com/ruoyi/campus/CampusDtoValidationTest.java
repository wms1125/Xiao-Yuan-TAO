package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.campus.dto.CampusAccountRechargeDTO;
import com.ruoyi.campus.dto.CampusCartAddDTO;
import com.ruoyi.campus.dto.CampusCartCheckDTO;
import com.ruoyi.campus.dto.CampusCartQuantityDTO;
import com.ruoyi.campus.dto.CampusCategorySaveDTO;
import com.ruoyi.campus.dto.CampusOrderCreateDTO;
import com.ruoyi.campus.dto.CampusProductCreateDTO;
import com.ruoyi.campus.dto.CampusProductStatusDTO;
import com.ruoyi.campus.dto.CampusProductUpdateDTO;
import com.ruoyi.campus.dto.CampusReviewAddDTO;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 请求 DTO Bean Validation 单元测试（校园淘）
 * 纯 JUnit，不依赖 Spring 容器与数据库，可直接运行。
 */
class CampusDtoValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static <T> boolean valid(T obj) {
        return VALIDATOR.validate(obj).isEmpty();
    }

    // ---------- 商品发布 ----------

    @Test
    @DisplayName("商品标题为空应校验失败")
    void 商品标题为空_校验失败() {
        CampusProductCreateDTO dto = new CampusProductCreateDTO();
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("30"));
        dto.setStock(5);
        assertFalse(valid(dto), "标题为空应触发校验错误");
    }

    @Test
    @DisplayName("商品库存为0应校验失败")
    void 商品库存为零_校验失败() {
        CampusProductCreateDTO dto = new CampusProductCreateDTO();
        dto.setTitle("二手显示器");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("30"));
        dto.setStock(0);
        assertFalse(valid(dto), "库存为 0 应触发校验错误");
    }

    @Test
    @DisplayName("合法商品应校验通过")
    void 合法商品_校验通过() {
        CampusProductCreateDTO dto = new CampusProductCreateDTO();
        dto.setTitle("二手显示器");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("30"));
        dto.setStock(5);
        assertTrue(valid(dto), "合法商品不应有校验错误");
    }

    // ---------- 购物车 ----------

    @Test
    @DisplayName("购买数量为负应校验失败")
    void 购物车数量为负_校验失败() {
        CampusCartAddDTO dto = new CampusCartAddDTO();
        dto.setProductId(1L);
        dto.setQuantity(-3);
        assertFalse(valid(dto), "数量为负应触发校验错误");
    }

    @Test
    @DisplayName("购物车数量为正应校验通过")
    void 购物车数量为正_校验通过() {
        CampusCartAddDTO dto = new CampusCartAddDTO();
        dto.setProductId(1L);
        dto.setQuantity(2);
        assertTrue(valid(dto), "数量为正不应有校验错误");
    }

    // ---------- 下单 ----------

    @Test
    @DisplayName("缺少收货人应校验失败")
    void 下单缺少收货人_校验失败() {
        CampusOrderCreateDTO dto = new CampusOrderCreateDTO();
        dto.setCartIds(List.of(1L));
        dto.setReceiverPhone("13800000000");
        dto.setAddress("1号宿舍楼");
        assertFalse(valid(dto), "缺少收货人应触发校验错误");
    }

    @Test
    @DisplayName("缺少购物车项应校验失败")
    void 下单缺少购物车项_校验失败() {
        CampusOrderCreateDTO dto = new CampusOrderCreateDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800000000");
        dto.setAddress("1号宿舍楼");
        assertFalse(valid(dto), "缺少购物车项应触发校验错误");
    }

    @Test
    @DisplayName("合法下单请求应校验通过")
    void 合法下单_校验通过() {
        CampusOrderCreateDTO dto = new CampusOrderCreateDTO();
        dto.setCartIds(List.of(1L));
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800000000");
        dto.setAddress("1号宿舍楼");
        assertTrue(valid(dto), "合法下单请求不应有校验错误");
    }

    // ---------- 评价 ----------

    @Test
    @DisplayName("评分越界(9)应校验失败")
    void 评价评分越界_校验失败() {
        CampusReviewAddDTO dto = new CampusReviewAddDTO();
        dto.setOrderId(1L);
        dto.setProductId(1L);
        dto.setRating(9);
        assertFalse(valid(dto), "评分越界应触发校验错误");
    }

    // ---------- 充值 ----------

    @Test
    @DisplayName("充值金额为0应校验失败")
    void 充值金额为零_校验失败() {
        CampusAccountRechargeDTO dto = new CampusAccountRechargeDTO();
        dto.setAmount(BigDecimal.ZERO);
        assertFalse(valid(dto), "充值金额为 0 应触发校验错误");
    }

    @Test
    @DisplayName("错误提示信息应为中文可读文案")
    void 校验提示为中文文案() {
        CampusCartAddDTO dto = new CampusCartAddDTO();
        dto.setProductId(1L);
        dto.setQuantity(-1);
        String msg = VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getMessage).reduce("", String::concat);
        assertTrue(msg.contains("购买数量至少为 1"), "应返回中文校验提示，实际: " + msg);
    }

    // ---------- 商品修改（v1.0.15 起由实体直收改为 DTO）----------

    @Test
    @DisplayName("商品修改：缺少主键应校验失败")
    void 商品修改缺少主键_校验失败() {
        CampusProductUpdateDTO dto = new CampusProductUpdateDTO();
        dto.setTitle("二手显示器");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("30"));
        dto.setStock(1);
        assertFalse(valid(dto), "缺少商品ID应触发校验错误");
    }

    @Test
    @DisplayName("商品修改：库存为负应校验失败")
    void 商品修改库存为负_校验失败() {
        CampusProductUpdateDTO dto = new CampusProductUpdateDTO();
        dto.setId(5L);
        dto.setTitle("二手显示器");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("30"));
        dto.setStock(-1);
        assertFalse(valid(dto), "库存为负应触发校验错误");
    }

    @Test
    @DisplayName("商品修改：字段齐备时校验通过")
    void 商品修改字段齐备_校验通过() {
        CampusProductUpdateDTO dto = new CampusProductUpdateDTO();
        dto.setId(5L);
        dto.setTitle("二手显示器");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("30"));
        dto.setStock(0);
        assertTrue(valid(dto), "库存为 0 属合法的下架态，应校验通过");
    }

    // ---------- 商品上下架 ----------

    @Test
    @DisplayName("商品上下架：状态取值越界应校验失败")
    void 商品上下架状态越界_校验失败() {
        CampusProductStatusDTO dto = new CampusProductStatusDTO();
        dto.setId(5L);
        dto.setStatus(2);
        assertFalse(valid(dto), "状态只能是 0/1，越界应触发校验错误");
    }

    // ---------- 分类新增/修改 ----------

    @Test
    @DisplayName("分类新增：名称为空应校验失败")
    void 分类名称为空_校验失败() {
        CampusCategorySaveDTO dto = new CampusCategorySaveDTO();
        dto.setName("   ");
        assertFalse(valid(dto), "名称空白应触发校验错误");
    }

    @Test
    @DisplayName("分类新增：新增场景无需主键，字段齐备时校验通过")
    void 分类新增字段齐备_校验通过() {
        CampusCategorySaveDTO dto = new CampusCategorySaveDTO();
        dto.setName("教材书籍");
        dto.setSort(1);
        assertTrue(valid(dto), "新增场景 id 为空属正常，应校验通过");
    }

    // ---------- 购物车数量与勾选 ----------

    @Test
    @DisplayName("购物车数量：为0应校验失败")
    void 购物车数量为零_校验失败() {
        CampusCartQuantityDTO dto = new CampusCartQuantityDTO();
        dto.setId(7L);
        dto.setQuantity(0);
        assertFalse(valid(dto), "数量为 0 应触发校验错误");
    }

    @Test
    @DisplayName("购物车勾选：取值越界应校验失败")
    void 购物车勾选越界_校验失败() {
        CampusCartCheckDTO dto = new CampusCartCheckDTO();
        dto.setId(7L);
        dto.setChecked(2);
        assertFalse(valid(dto), "勾选状态只能是 0/1，越界应触发校验错误");
    }
}
