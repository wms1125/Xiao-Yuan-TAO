package com.ruoyi.campus;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

/**
 * 校园淘 Controller 层单测公共夹具。
 *
 * <p>解决两个静态上下文依赖：
 * <ul>
 *   <li>{@code SecurityUtils.getUserId()/getUsername()} 读 SecurityContextHolder</li>
 *   <li>{@code BaseController.startPage()} 读 RequestContextHolder 中的请求参数</li>
 * </ul>
 */
public final class CampusTestSupport {

    private CampusTestSupport() {
    }

    /** 用户 ID 为 1 时 {@code SecurityUtils.isAdmin} 判定为管理员 */
    public static final long ADMIN_USER_ID = 1L;

    /**
     * 以指定身份建立登录态。
     *
     * @param userId   用户 ID（1 视为管理员）
     * @param userName 登录账号（{@code SecurityUtils.getUsername()} 取自 SysUser.userName）
     */
    public static void loginAs(long userId, String userName) {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setUserName(userName);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUser(sysUser);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, null));
    }

    /** 提供最小请求上下文，使分页接口（startPage）可用 */
    public static void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    /** 清理线程上下文，避免用例之间互相串扰 */
    public static void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        PageHelper.clearPage();
    }
}
