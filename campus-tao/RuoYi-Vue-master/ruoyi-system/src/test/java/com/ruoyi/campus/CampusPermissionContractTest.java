package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限串契约守护（校园淘）
 *
 * <p>解决一个<b>e2e 永远抓不到</b>的失效模式：{@code @PreAuthorize} 里把权限串写错一个字。</p>
 *
 * <p><b>为什么 e2e 抓不到</b>：{@code SysPermissionService} 对管理员直接注入
 * {@code "*:*:*"}，而 {@code e2e_business.py} 的卖家/运营侧全部用 {@code admin} 账号跑
 * （第 2 行注释即写明「双账号：admin 卖家 / ry 买家」）。也就是说
 * <b>卖家侧的每一条 {@code @PreAuthorize} 在 e2e 里都被通配权限放行了</b>，
 * 权限串写成 {@code campus:product:remvoe} 也一样"全链路通过"。
 * 上线后非管理员一点按钮就是 403，而 CI 永远是绿的。</p>
 *
 * <p>本测试把 {@code @PreAuthorize} 里的权限串与数据库种子里
 * {@code sys_menu.perms} 对齐：注解引用了不存在的权限 → 红。
 * 纯静态，不连库，毫秒级。</p>
 */
@DisplayName("权限串契约：注解 ↔ sys_menu.perms")
class CampusPermissionContractTest {

    private static final String CONTROLLER_PACKAGE = "com.ruoyi.campus.controller";

    /** Flyway 迁移目录（相对项目根），V1 为基线、V2~V4 由首次启动补齐 */
    private static final String MIGRATION_DIR = "RuoYi-Vue-master/ruoyi-admin/src/main/resources/db/migration/";

    /** 权限串格式：campus:<模块>:<动作>，全小写 */
    private static final Pattern PERM_PATTERN = Pattern.compile("^campus:[a-z]+:[a-z]+$");

    /** 从 SpEL 里取 hasPermi('...') / hasAnyPermi('a','b') 中的字面量 */
    private static final Pattern HAS_PERMI = Pattern.compile("has(?:Any)?Permi\\(([^)]*)\\)");

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    /** 种子里应至少这么多条 campus 权限；数量对不上说明解析或文件已失效 */
    private static final int MIN_EXPECTED_SEED_PERMS = 20;

    /** 注解侧应至少这么多条权限串；数量对不上说明扫描失效 */
    private static final int MIN_EXPECTED_ANNOTATED_PERMS = 25;

    @Test
    @DisplayName("前置：两侧都真的解析到了数据（防止零结果假通过）")
    void bothSidesParsed() {
        Set<String> annotated = annotatedPermissions();
        Set<String> seeded = seededPermissions();

        assertTrue(annotated.size() >= MIN_EXPECTED_ANNOTATED_PERMS,
                "只从注解里解析到 " + annotated.size() + " 条权限串，扫描器可能已失效：" + annotated);
        assertTrue(seeded.size() >= MIN_EXPECTED_SEED_PERMS,
                "只从 SQL 种子解析到 " + seeded.size() + " 条权限，路径或文件可能已失效");
    }

    @Test
    @DisplayName("守护：@PreAuthorize 引用的权限必须在 sys_menu.perms 里存在")
    void annotatedPermissionsExistInSeed() {
        Set<String> seeded = seededPermissions();
        Set<String> missing = new TreeSet<>();

        for (String perm : annotatedPermissions()) {
            if (!seeded.contains(perm)) {
                missing.add(perm);
            }
        }

        assertTrue(missing.isEmpty(),
                "以下权限串在 @PreAuthorize 里被引用，但 sys_menu 里没有种下——"
                        + "非管理员调用会一律 403，而 e2e（admin 有 *:*:*）永远发现不了：" + missing);
    }

    @Test
    @DisplayName("守护：权限串必须符合 campus:<模块>:<动作>，且不得使用通配符")
    void annotatedPermissionsAreWellFormed() {
        List<String> malformed = new ArrayList<>();

        for (String perm : annotatedPermissions()) {
            if (!PERM_PATTERN.matcher(perm).matches()) {
                malformed.add(perm);
            }
        }

        assertTrue(malformed.isEmpty(),
                "以下权限串不符合 campus:<模块>:<动作> 格式（大小写/分隔符写错会导致永久 403）：" + malformed);
    }

    @Test
    @DisplayName("守护：@PreAuthorize 不得写成通配权限（等于对所有人开放）")
    void annotationsMustNotUseWildcard() {
        List<String> wildcard = new ArrayList<>();

        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
                if (annotation == null) {
                    continue;
                }
                if (annotation.value().contains("*:*:*")) {
                    wildcard.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(wildcard.isEmpty(),
                "以下端点用通配权限做了 @PreAuthorize，等价于不做校验：" + wildcard);
    }

    @Test
    @DisplayName("守护：两份 SQL（V1 基线与全量脚本）的 campus 权限集合必须一致")
    void baselineAndFullScriptAgree() {
        Set<String> fromV1 = permissionsInFile(repoFile(MIGRATION_DIR + "V1__baseline_campus_tao.sql"));
        Set<String> fromFullScript = permissionsInFile(repoFile("sql/campus_tao_full.sql"));

        Set<String> onlyInV1 = new TreeSet<>(fromV1);
        onlyInV1.removeAll(fromFullScript);
        Set<String> onlyInFull = new TreeSet<>(fromFullScript);
        onlyInFull.removeAll(fromV1);

        assertFalse(fromV1.isEmpty(), "V1 迁移脚本里没解析到任何 campus 权限，路径可能已变");
        assertTrue(onlyInV1.isEmpty() && onlyInFull.isEmpty(),
                "V1 迁移与全量脚本的 campus 权限不一致——新库按 V1 建、一键脚本按 dump 建，"
                        + "改了 V1 却忘了重新导出 dump 就会出现这种分叉。"
                        + "仅 V1 有：" + onlyInV1 + "；仅 dump 有：" + onlyInFull);
    }

    // ---------------------------------------------------------------- 解析

    /** 收集所有 campus 控制器上 @PreAuthorize 里引用的权限串 */
    private static Set<String> annotatedPermissions() {
        Set<String> result = new LinkedHashSet<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
                if (annotation == null) {
                    continue;
                }
                Matcher call = HAS_PERMI.matcher(annotation.value());
                while (call.find()) {
                    Matcher quoted = QUOTED.matcher(call.group(1));
                    while (quoted.find()) {
                        result.add(quoted.group(1));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 数据库最终状态下的全部 campus 权限。
     *
     * <p><b>为什么取全部迁移的并集，而不是只看 V1</b>：一键脚本
     * {@code 1-初始化数据库.bat} 导入的 {@code campus_tao_full.sql} 是 <b>V1 基线</b>，
     * 首次启动时由 {@code baseline-on-migrate: true} + {@code baseline-version: 1}
     * 把 V2~V4 补上。所以运行态的权限集合 = V1 ∪ V2 ∪ V3 ∪ V4。
     * 只看 V1 会把 V2/V3 新增的买家侧权限误判成"不存在"。</p>
     */
    private static Set<String> seededPermissions() {
        Set<String> result = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(repoFile(MIGRATION_DIR))) {
            List<Path> migrations = files
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
            assertFalse(migrations.isEmpty(), "迁移目录里没有 V*.sql，路径可能已变：" + MIGRATION_DIR);
            for (Path migration : migrations) {
                result.addAll(permissionsInFile(migration));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描迁移目录失败：" + MIGRATION_DIR, e);
        }
        return result;
    }

    private static Set<String> permissionsInFile(Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取权限种子失败：" + file, e);
        }
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("'(campus:[a-zA-Z]+:[a-zA-Z]+)'").matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * 从模块目录向上找到仓库内的文件。
     *
     * <p>刻意"找不到就报错"而不是跳过：路径失效时静默跳过，等于这个守护悄悄消失。</p>
     */
    private static Path repoFile(String relativeToProjectRoot) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && dir != null; depth++) {
            Path candidate = dir.resolve(relativeToProjectRoot);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到文件：" + relativeToProjectRoot
                + "（从 " + Paths.get("").toAbsolutePath() + " 向上查找 6 层）");
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
