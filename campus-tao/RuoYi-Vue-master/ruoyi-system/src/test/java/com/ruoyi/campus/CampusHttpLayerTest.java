package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ruoyi.campus.controller.CampusProductController;
import com.ruoyi.campus.domain.CampusProduct;
import com.ruoyi.campus.exception.CampusGlobalExceptionHandler;
import com.ruoyi.campus.service.ICampusProductService;

/**
 * HTTP 层测试（校园淘）
 *
 * <p>补的是此前完全空白的一层：既有 23 个后端测试类**直接调用 Controller 方法**，
 * 因此 {@code @Valid} 的参数绑定、JSON 反序列化、统一异常响应结构都没有被测到——
 * 这些恰恰是最容易在改动中悄悄坏掉、而单测看不见的部分。</p>
 *
 * <p><b>本类测不到什么（如实说明）</b>：{@code standaloneSetup} 没有 Spring Security
 * 过滤器链，也没有 AOP 代理，所以 {@code @PreAuthorize} 在这里<b>不会生效</b>，
 * 越权拦截由 e2e（真实栈）与 {@code CampusPermissionGuardTest}（注解覆盖守护）共同保证。
 * 同样，日期格式由 ruoyi-framework 的 JacksonConfig 决定，standalone 用默认 ObjectMapper，
 * 故此处不对日期格式作断言——用测试自己的配置去断言生产行为是自欺。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HTTP 层：参数绑定 / 校验 / 统一异常响应")
class CampusHttpLayerTest {

    private static final long SELLER_ID = 2L;

    private static final String VALID_BODY = """
            {"title":"九成新台灯","categoryId":3,"price":59.90,"stock":2,"status":0}
            """;

    @Mock
    private ICampusProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CampusProductController controller = new CampusProductController();
        ReflectionTestUtils.setField(controller, "productService", productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CampusGlobalExceptionHandler())
                .build();
        CampusTestSupport.loginAs(SELLER_ID, "seller");
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    @Test
    @DisplayName("合法请求体：200 且 DTO 字段逐项绑定到实体，卖家身份取自登录态")
    void createBindsDtoAndDerivesSeller() throws Exception {
        when(productService.insertCampusProduct(any(CampusProduct.class), anyLong(), any())).thenReturn(1);

        mockMvc.perform(post("/campus/product").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<CampusProduct> captor = ArgumentCaptor.forClass(CampusProduct.class);
        verify(productService).insertCampusProduct(captor.capture(), eq(SELLER_ID), eq("seller"));

        CampusProduct saved = captor.getValue();
        assertEquals("九成新台灯", saved.getTitle());
        assertEquals(3L, saved.getCategoryId());
        // BigDecimal 必须按数值比较：equals 会把 59.90 与 59.9 判为不等
        assertEquals(0, new BigDecimal("59.90").compareTo(saved.getPrice()));
        assertEquals(2, saved.getStock());
    }

    @Test
    @DisplayName("负向：标题为空触发 @NotBlank，返回 400 且不落库")
    void blankTitleRejectedBeforeService() throws Exception {
        String body = """
                {"title":"","categoryId":3,"price":59.90,"stock":2}
                """;

        mockMvc.perform(post("/campus/product").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        // 校验必须在进入业务层之前拦住，否则脏数据会写库
        verify(productService, never()).insertCampusProduct(any(), anyLong(), any());
    }

    @Test
    @DisplayName("负向：价格为 0 触发 @DecimalMin，返回 400")
    void zeroPriceRejected() throws Exception {
        String body = """
                {"title":"白送台灯","categoryId":3,"price":0,"stock":1}
                """;

        mockMvc.perform(post("/campus/product").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(productService, never()).insertCampusProduct(any(), anyLong(), any());
    }

    @Test
    @DisplayName("负向：缺少必填的 categoryId，返回 400（而不是 NPE 500）")
    void missingCategoryRejected() throws Exception {
        String body = """
                {"title":"台灯","price":10.00,"stock":1}
                """;

        mockMvc.perform(post("/campus/product").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(productService, never()).insertCampusProduct(any(), anyLong(), any());
    }

    @Test
    @DisplayName("未预期异常：由全局处理器兜底为 {code:500,msg}，不把堆栈抛给前端")
    void unexpectedExceptionMappedToAjaxError() throws Exception {
        when(productService.selectCampusProductById(9L)).thenThrow(new IllegalStateException("数据库连接中断"));

        mockMvc.perform(get("/campus/product/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("数据库连接中断"));
    }

    @Test
    @DisplayName("查询详情：金额按数值原样返回，不被转成字符串")
    void detailKeepsNumericAmount() throws Exception {
        CampusProduct product = new CampusProduct();
        product.setId(9L);
        product.setTitle("二手自行车");
        product.setPrice(new BigDecimal("199.50"));
        when(productService.selectCampusProductById(9L)).thenReturn(product);

        mockMvc.perform(get("/campus/product/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("二手自行车"))
                .andExpect(jsonPath("$.data.price").value(199.50));
    }
}
