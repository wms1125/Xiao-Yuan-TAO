package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ruoyi.campus.controller.CampusProductController;
import com.ruoyi.campus.service.ICampusProductService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.PermissionService;

/**
 * 越权拦截的<b>运行时</b>断言（校园淘）
 *
 * <p>补的是权限证据链上唯一缺失的一环：</p>
 * <pre>
 *   CampusPermissionGuardTest      —— 有没有写注解（静态）
 *   CampusPermissionContractTest   —— 写的权限串对不对（静态，对齐 sys_menu）
 *   CampusAuthorizationTest（本类）—— 注解到底拦不拦得住（运行时，真实拦截器 + 真实 PermissionService）
 * </pre>
 *
 * <p><b>为什么放在 ruoyi-admin</b>：需要真实的 {@code PermissionService}
 * （{@code ruoyi-framework} 里的 {@code @Service("ss")}），而 framework 依赖 system，
 * 反向依赖不可行。本类用 {@code @SpringJUnitConfig} 只装配三件东西——
 * 真实的 {@code PermissionService}、真实的 {@code CampusProductController}、
 * 一个 mock 的 service——不启动 Web 容器、不连数据库，因此能在 CI 的 backend job 里跑。</p>
 *
 * <p><b>本类同时把一件事钉成可执行事实</b>：管理员持有 {@code *:*:*}，
 * 所有 {@code @PreAuthorize} 对它一律放行（{@code hasPermissions} 第 157 行）。
 * 这正是 {@code e2e_business.py} 用 admin 跑卖家链路却<b>验证不到任何权限</b>的原因，
 * 也是 {@code CampusPermissionContractTest} 存在的理由。</p>
 */
@SpringJUnitConfig(CampusAuthorizationTest.Config.class)
@DisplayName("越权拦截：@PreAuthorize 的运行时行为")
class CampusAuthorizationTest {

    private static final long USER_ID = 2L;

    private static final String PERM_REMOVE = "campus:product:remove";

    @Autowired
    private CampusProductController productController;

    @Autowired
    private ICampusProductService productService;

    /** 生产环境靠 SecurityConfig 上的 @EnableMethodSecurity 让 @PreAuthorize 生效，这里等价复现 */
    @Configuration
    @EnableMethodSecurity
    static class Config {

        /** bean 名必须是 ss：注解里的 SpEL 写的是 @ss.hasPermi(...) */
        @Bean("ss")
        PermissionService permissionService() {
            return new PermissionService();
        }

        @Bean
        ICampusProductService productService() {
            return mock(ICampusProductService.class);
        }

        @Bean
        CampusProductController campusProductController() {
            return new CampusProductController();
        }
    }

    @BeforeEach
    void bindRequestAndResetMock() {
        // PermissionService.hasPermi 会把权限串写进请求上下文（供审计用），
        // 真实调用永远发生在一次 HTTP 请求里，这里补一个请求作用域以还原该前提
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        // Spring 上下文在用例间共享，mock 是单例：不 reset 会把上一个用例的调用累积进来，
        // verify(...) 的默认 times(1) 就会误报——先跑的那个绿、后跑的假红
        reset(productService);
        when(productService.deleteCampusProductByIds(any(), anyLong())).thenReturn(1);
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("持有权限：放行，且调用真的落到 service")
    void allowedWhenHoldingPermission() {
        loginWith(Set.of(PERM_REMOVE));

        assertDoesNotThrow(() -> productController.remove(new Long[] {1L}));
        verify(productService).deleteCampusProductByIds(any(), anyLong());
    }

    @Test
    @DisplayName("负向：不持有权限 → AccessDeniedException，且 service 一次都没被调用")
    void deniedWhenMissingPermission() {
        loginWith(Set.of("campus:product:list"));

        assertThrows(AccessDeniedException.class, () -> productController.remove(new Long[] {1L}));
        // 只断言"抛异常"是不够的：若拦截器在 service 之后才抛，数据已经被删了
        verify(productService, never()).deleteCampusProductByIds(any(), anyLong());
    }

    @Test
    @DisplayName("负向：权限集合为空 → 同样拒绝（未登录/无任何授权）")
    void deniedWhenNoPermissionAtAll() {
        loginWith(Collections.emptySet());

        assertThrows(AccessDeniedException.class, () -> productController.remove(new Long[] {1L}));
        verify(productService, never()).deleteCampusProductByIds(any(), anyLong());
    }

    @Test
    @DisplayName("事实：管理员持 *:*:*，任何 @PreAuthorize 都放行——e2e 因此验证不到权限")
    void adminWildcardBypassesEveryCheck() {
        loginWith(Set.of("*:*:*"));

        assertDoesNotThrow(() -> productController.remove(new Long[] {1L}));
        verify(productService).deleteCampusProductByIds(any(), anyLong());
    }

    /** 把登录态塞进 SecurityContext：PermissionService 与 SecurityUtils 都从这里取用户 */
    private static void loginWith(Set<String> permissions) {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        user.setUserName("ry");
        // 必须用 4 参构造：2 参构造不会设置 userId，getUserId() 会返回 null，
        // 于是越权断言"通过"了、service 却拿到 null 用户——一个安静的假绿
        LoginUser loginUser = new LoginUser(USER_ID, null, user, permissions);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
