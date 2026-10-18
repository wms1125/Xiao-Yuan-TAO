package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.campus.security.SelfServiceEndpoint;
import com.ruoyi.common.annotation.Anonymous;

/**
 * 权限注解覆盖守护（校园淘）
 *
 * <p>解决一个具体的失效模式：<b>新增一个写接口，忘记加 {@code @PreAuthorize}</b>。
 * 这类漏洞在单测里看不见（Controller 单测直接调方法，不经过 AOP），
 * 在 e2e 里也未必被覆盖（没写到那条断言就永远发现不了），
 * 最终表现为"任何登录用户都能调"的越权。</p>
 *
 * <p>做法是把"约定"变成可执行断言：扫描所有 {@code @RestController}，
 * 凡声明为 POST / PUT / DELETE（含 {@code @RequestMapping} 指定这些方法）的端点，
 * 必须满足三者之一：</p>
 * <ol>
 *   <li>带 {@code @PreAuthorize}——需要按钮级权限；</li>
 *   <li>带 {@code @Anonymous}——匿名开放（如支付回调），是显式决定；</li>
 *   <li>带 {@code @SelfServiceEndpoint}——只操作本人数据、无需按钮权限，
 *       但必须写下数据隔离依据（见该注解 Javadoc）。</li>
 * </ol>
 *
 * <p>三者都不满足才报错。这样"不带权限"永远是一个<b>写下来的决定</b>，
 * 而不是忘写注解的副作用。</p>
 *
 * <p>本类只做静态检查，不启动 Spring 上下文，因此跑得很快，适合放在每次 {@code mvn verify} 里。</p>
 */
@DisplayName("权限注解覆盖守护")
class CampusPermissionGuardTest {

    private static final String CONTROLLER_PACKAGE = "com.ruoyi.campus.controller";

    /** 扫描失败（比如包名写错）时本测试会"零违规"假通过，故用已知数量兜底 */
    private static final int MIN_EXPECTED_CONTROLLERS = 15;

    @Test
    @DisplayName("前置：控制器扫描有效（防止零结果导致假通过）")
    void scanFindsControllers() {
        List<Class<?>> controllers = controllers();

        assertTrue(controllers.size() >= MIN_EXPECTED_CONTROLLERS,
                "只扫描到 " + controllers.size() + " 个控制器，包名或扫描器可能已失效");
    }

    @Test
    @DisplayName("守护：变更类端点必须声明 @PreAuthorize / @Anonymous / @SelfServiceEndpoint 之一")
    void mutatingEndpointsDeclarePermission() {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isMutating(method)) {
                    continue;
                }
                checked++;
                if (method.isAnnotationPresent(Anonymous.class)) {
                    // 匿名开放是显式决定（如支付回调），允许
                    continue;
                }
                if (method.isAnnotationPresent(SelfServiceEndpoint.class)) {
                    // 自助端点：只改本人数据。允许，但注解必须带理由（value 不能为空），
                    // 且实现侧必须把 userId 下推到 SQL 的 where 条件——见 SelfServiceEndpoint Javadoc
                    continue;
                }
                if (!method.isAnnotationPresent(PreAuthorize.class)) {
                    missing.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(checked >= 30, "只检查到 " + checked + " 个变更类端点，扫描逻辑可能已失效");
        assertTrue(missing.isEmpty(),
                "以下变更类端点既无 @PreAuthorize、也未声明 @Anonymous / @SelfServiceEndpoint"
                        + "（任何登录用户都可调用，属越权风险）：" + new TreeSet<>(missing));
    }

    @Test
    @DisplayName("守护：@SelfServiceEndpoint 必须写明数据隔离依据（空理由视为未声明）")
    void selfServiceMarkerRequiresRationale() {
        List<String> blank = new ArrayList<>();

        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                SelfServiceEndpoint marker = method.getAnnotation(SelfServiceEndpoint.class);
                if (marker != null && marker.value().trim().isEmpty()) {
                    blank.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(blank.isEmpty(),
                "以下端点标注了 @SelfServiceEndpoint 却没写理由，等于把越权风险留白："
                        + new TreeSet<>(blank));
    }

    /** 是否为"会改数据"的端点：显式 POST/PUT/DELETE，或 @RequestMapping 指定了这些方法 */
    private static boolean isMutating(Method method) {
        if (method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)) {
            return true;
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        for (RequestMethod candidate : mapping.method()) {
            if (candidate == RequestMethod.POST || candidate == RequestMethod.PUT
                    || candidate == RequestMethod.DELETE || candidate == RequestMethod.PATCH) {
                return true;
            }
        }
        return false;
    }

    /** 扫描 campus 控制器包下所有 @RestController */
    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            try {
                found.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("控制器类无法加载: " + definition.getBeanClassName(), e);
            }
        }
        return found;
    }
}
