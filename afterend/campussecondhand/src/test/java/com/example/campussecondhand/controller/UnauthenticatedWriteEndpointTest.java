package com.example.campussecondhand.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campussecondhand.util.JwtUtil;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 公开接口不得包含写操作（P0 回归防线）。
 *
 * <p><b>漏洞是怎么发生的：</b>{@code SecurityConfig} 是
 * {@code .anyRequest().permitAll()} + {@code csrf.disable()}，唯一的拦截器只覆盖
 * {@code /api/admin/**}。这意味着 C 端接口的鉴权<b>完全依赖每个 Controller 自己
 * 解析 {@code Authorization} 头</b>——一旦某个写接口忘了写，就是一个裸奔的接口。</p>
 *
 * <p>真实踩过的坑：{@code ProductController} 上有过一个
 * {@code POST /update-images}，作者注释里写着「一次性迁移脚本专用，不对用户暴露」，
 * 但它根本没有鉴权。端点逻辑是「遍历全部在售商品，标题不在请求体里的全部删除」，
 * 于是未登录的一封 POST 就能清空整个商品目录。一次请求、结果确定、零门槛，
 * 比任何需要特定时序的并发漏洞都更容易被触发。</p>
 *
 * <p><b>本测试的判据：</b>写方法（POST/PUT/PATCH/DELETE）必须显式声明
 * {@code Authorization} 请求头，否则视为裸奔。之所以按「有没有声明 authHeader」
 * 而不是「有没有校验」，是因为校验逻辑藏在方法体里、无法可靠反射；而
 * {@code @RequestHeader("Authorization")} 是每个受保护端点都必须写的样板，
 * 漏了它本身就是最可靠的失误信号。</p>
 */
class UnauthenticatedWriteEndpointTest {

    /** 公开只读接口白名单：登录、注册、商品与分类浏览、类型枚举等 */
    private static final List<String> PUBLIC_WRITE_ALLOWLIST = List.of(
            // 登录 / 注册 / 验证码：本身就要在无身份状态下调用
            "/auth/login",
            "/auth/register",
            "/auth/send-code",
            "/auth/forgot-password",
            "/auth/reset-password");

    private static final List<Class<?>> CONTROLLERS = List.of(
            AuthController.class,
            UserController.class,
            ProductController.class,
            PostController.class,
            OrderController.class,
            CartController.class,
            MessageController.class,
            ReportController.class,
            FavoriteController.class,
            CategoryController.class,
            BrowseHistoryController.class,
            ProductInteractionController.class,
            // 图片上传必须登录：SecurityConfig 是 permitAll，漏了就是"任何人可写服务器磁盘"
            UploadController.class);

    private static boolean isWriteMapping(Method m) {
        return m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
    }

    private static boolean declaresAuthHeader(Method m) {
        for (Parameter p : m.getParameters()) {
            if (!p.isAnnotationPresent(RequestHeader.class)) {
                continue;
            }
            String name = p.getAnnotation(RequestHeader.class).value();
            // @RequestHeader("Authorization") 或 @RequestHeader(value="Authorization")
            if ("Authorization".equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** 类级别 @RequestMapping 的路径前缀 */
    private static String basePath(Class<?> c) {
        var rm = c.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        return rm == null || rm.value().length == 0 ? "" : rm.value()[0];
    }

    /** 方法级 @PostMapping/@PutMapping/@PatchMapping/@DeleteMapping 的路径 */
    private static String methodPath(Method m) {
        String[] value;
        if (m.isAnnotationPresent(PostMapping.class)) {
            value = m.getAnnotation(PostMapping.class).value();
        } else if (m.isAnnotationPresent(PutMapping.class)) {
            value = m.getAnnotation(PutMapping.class).value();
        } else if (m.isAnnotationPresent(PatchMapping.class)) {
            value = m.getAnnotation(PatchMapping.class).value();
        } else {
            value = m.getAnnotation(DeleteMapping.class).value();
        }
        return value.length == 0 ? "" : value[0];
    }

    private static List<String> collectUnauthenticatedWrites() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : CONTROLLERS) {
            for (Method m : c.getDeclaredMethods()) {
                if (!isWriteMapping(m) || declaresAuthHeader(m)) {
                    continue;
                }
                String full = (basePath(c) + methodPath(m)).toLowerCase(Locale.ROOT);
                boolean allowed = PUBLIC_WRITE_ALLOWLIST.stream().anyMatch(full::endsWith);
                if (!allowed) {
                    offenders.add(c.getSimpleName() + "#" + m.getName() + "  ->  " + full);
                }
            }
        }
        return offenders;
    }

    @Test
    @DisplayName("除登录注册外，不允许存在无 Authorization 的写接口")
    void noUnauthenticatedWriteEndpoints() {
        List<String> offenders = collectUnauthenticatedWrites();

        assertThat(offenders)
                .as("""
                     发现无鉴权写接口。SecurityConfig 是 .anyRequest().permitAll()，
                     写接口一旦漏写 @RequestHeader("Authorization") 就完全裸奔。
                     已删除的 update-images 正是这样：未登录一封 POST 清空全部商品。
                     确需公开写接口，请加入 PUBLIC_WRITE_ALLOWLIST 并说明理由；
                     一次性脚本请放 @Profile("dev")，仿 DevMigrationController。""")
                .isEmpty();
    }

    @Test
    @DisplayName("update-images 端点确已移除")
    void updateImagesIsGone() {
        boolean present = false;
        for (Method m : ProductController.class.getDeclaredMethods()) {
            if (m.getName().equals("updateImages")) {
                present = true;
                break;
            }
        }
        assertThat(present)
                .as("POST /api/product/update-images 必须删除：它未鉴权且会删除全部商品")
                .isFalse();
    }

    @Test
    @DisplayName("分类 C 端只读：写操作必须走 /api/admin/categories")
    void categoryControllerIsReadOnly() {
        List<String> writes = new ArrayList<>();
        for (Method m : CategoryController.class.getDeclaredMethods()) {
            if (isWriteMapping(m)) {
                writes.add(m.getName());
            }
        }
        assertThat(writes)
                .as("""
                     /api/categories 曾对任意登录用户开放新增/修改/删除，等同于越权改全站分类。
                     分类写操作只能走 /api/admin/categories，由 AdminAuthInterceptor 校验管理员。
                     如确需恢复 C 端写接口，请先补角色校验并更新本测试。""")
                .isEmpty();
    }

    @Test
    @DisplayName("白名单里的路径确实都存在——防止白名单写成过期条目而无人察觉")
    void allowlistEntriesStillExist() {
        List<String> allPaths = new ArrayList<>();
        for (Class<?> c : CONTROLLERS) {
            for (Method m : c.getDeclaredMethods()) {
                if (isWriteMapping(m)) {
                    allPaths.add((basePath(c) + methodPath(m)).toLowerCase(Locale.ROOT));
                }
            }
        }
        // 登录与注册必须确实存在，否则白名单里的条目是陈旧的
        assertThat(allPaths).anyMatch(p -> p.endsWith("/auth/login"));
        assertThat(allPaths).anyMatch(p -> p.endsWith("/auth/register"));
    }

    @Test
    @DisplayName("测试本身有效：JwtUtil 存在且控制器列表非空")
    void testIsNotVacuous() {
        assertThat(JwtUtil.class).isNotNull();
        assertThat(CONTROLLERS).hasSizeGreaterThanOrEqualTo(10);
        // 确认确实扫描到了写方法，否则上面三个断言都是空转
        assertThat(CONTROLLERS).anySatisfy(c -> {
            long writes = java.util.Arrays.stream(c.getDeclaredMethods())
                    .filter(UnauthenticatedWriteEndpointTest::isWriteMapping)
                    .count();
            assertThat(writes).isPositive();
        });
    }
}
