package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.campus.dto.CampusAccountRechargeDTO;
import com.ruoyi.campus.dto.CampusCartAddDTO;
import com.ruoyi.campus.dto.CampusOrderCreateDTO;
import com.ruoyi.campus.dto.CampusProductCreateDTO;
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
}
