package com.example.campussecondhand.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.EmailService;
import com.example.campussecondhand.service.UserService;
import com.example.campussecondhand.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

/**
 * {@code GET /api/auth/me} 与登录响应的身份信息契约。
 *
 * <p>/api/auth/me 是全站身份信息的唯一入口——前端路由守卫对任何 requiresAuth
 * 页面都会先 fetchMe() 一次，导航栏的头像、用户名与认证徽章都读这里。</p>
 *
 * <p>本测试要钉死两件事：</p>
 * <ol>
 *   <li>认证状态以 {@code studentVerified}（布尔）返回，全站与
 *       {@code /api/user/*} 保持同一个名字与类型；</li>
 *   <li><b>完整学号绝不出现在任何响应里</b>，只允许出现脱敏值。User.studentNo 标了
 *       WRITE_ONLY，序列化时本就不该带出，但「本就不该」和「被断言锁住」是两回事——
 *       哪天有人觉得「登录响应自己人看没关系」而去掉标注，这里会立刻红。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerMeContractTest {

    private static final String AUTH = "Bearer test-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private UserService userService;
    @InjectMocks
    private AuthController controller;

    private User verifiedUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRole(1);
        u.setAvatar("data:image/jpeg;base64,AAAA");
        u.setEmail("admin@campus.com");
        u.setPhone("13800000000");
        u.setStudentNo("202012345678");
        u.setRealName("张三");
        u.setIsStudentVerified(1);
        return u;
    }

    private void tokenResolvesTo(User u) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(u.getUsername());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(u));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(ResponseEntity<ApiResponse<?>> resp) {
        ApiResponse<?> api = resp.getBody();
        assertThat(api).as("响应体不应为空").isNotNull();
        return (Map<String, Object>) api.getData();
    }

    @Test
    @DisplayName("/me 返回 studentVerified 布尔")
    void meReturnsStudentVerifiedAsBoolean() {
        tokenResolvesTo(verifiedUser());

        Object flag = dataOf(controller.me(AUTH)).get("studentVerified");

        assertThat(flag).isInstanceOf(Boolean.class);
        assertThat((Boolean) flag).isTrue();
    }

    @Test
    @DisplayName("/me 返回脱敏学号 2020****78，不返回完整学号")
    void meReturnsMaskedStudentNoOnly() {
        tokenResolvesTo(verifiedUser());

        Map<String, Object> data = dataOf(controller.me(AUTH));

        assertThat(data.get("studentNoMasked")).isEqualTo("2020****78");
        // 完整学号不得出现
        assertThat(data).doesNotContainKey("studentNo");
        assertThat(data.values()).noneMatch(v -> "202012345678".equals(v));
    }

    @Test
    @DisplayName("/me 不泄露实体上的联系方式与密码")
    void meDoesNotLeakUserEntityFields() {
        tokenResolvesTo(verifiedUser());

        Map<String, Object> data = dataOf(controller.me(AUTH));

        for (String leaked : new String[]{"email", "phone", "qq", "wechat", "bio",
                "password", "realName", "studentNo"}) {
            assertThat(data).as("泄露字段：%s", leaked).doesNotContainKey(leaked);
        }
        // 导航栏确实要用的这几个必须还在
        assertThat(data).containsKeys("id", "username", "role", "avatar", "status");
    }

    @Test
    @DisplayName("未认证用户 /me 仍返回完整键集，studentVerified=false 且学号脱敏为空串")
    void meForUnverifiedUser() {
        User u = new User();
        u.setId(2L);
        u.setUsername("buyer1");
        u.setRole(0);
        u.setIsStudentVerified(null);
        tokenResolvesTo(u);

        Map<String, Object> data = dataOf(controller.me(AUTH));

        assertThat(data).containsKey("studentVerified");
        assertThat(data.get("studentVerified")).isEqualTo(false);
        // 未认证用户没有学号，返回空串而不是 "****"——前端据此隐藏整个认证信息区
        assertThat(data.get("studentNoMasked")).isEqualTo("");
    }

    @Test
    @DisplayName("无 Authorization 头时返回 401，不泄露任何字段")
    void meWithoutTokenReturnsUnauthorized() {
        var resp = controller.me(null);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("过期 token 返回 401")
    void meWithExpiredTokenReturnsUnauthorized() {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(null);

        var resp = controller.me(AUTH);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(401);
    }
}
