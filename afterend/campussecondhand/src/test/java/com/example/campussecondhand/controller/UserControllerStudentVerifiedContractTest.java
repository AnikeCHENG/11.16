package com.example.campussecondhand.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.AccountRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.PostService;
import com.example.campussecondhand.service.ReviewService;
import com.example.campussecondhand.service.StudentVerifyService;
import com.example.campussecondhand.util.JwtUtil;
import java.time.LocalDateTime;
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
 * 学生认证状态字段的 JSON 契约。
 *
 * <p><b>这个测试要钉死的是一个已经真实发生过的 bug。</b>认证状态此前在全站有四种
 * 写法：本控制器的 {@code /hall-profile} 叫 {@code verified}、{@code /profile}
 * 干脆没返回、{@code /student-verify} 与 {@code /user/{id}} 叫
 * {@code isStudentVerified}，而 {@code MessageService} 与 {@code OrderService}
 * 叫 {@code studentVerified}。</p>
 *
 * <p>名字一散，{@code Profile.vue} 写成了 {@code data.isStudentVerified === true}，
 * 而 {@code /profile} 返回的该字段是 {@code undefined}——于是
 * {@code undefined === true} 恒为 false。表现是：学生认证成功的瞬间徽章会出现
 * （前端在提交回调里乐观置了 true），但<b>一刷新页面徽章就消失、认证表单重新出现</b>，
 * 用户再点提交就会撞上后端「已通过学生认证」的 400。</p>
 *
 * <p>这里对每个返回点都断言「有 {@code studentVerified} 这个键，且没有旧键名残留」，
 * 避免下次新增接口时又随手沿用实体字段名 {@code isStudentVerified}。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerStudentVerifiedContractTest {

    private static final String AUTH = "Bearer test-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StudentVerifyService studentVerifyService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private PostService postService;
    @Mock
    private AccountRepository accountRepository;
    @InjectMocks
    private UserController controller;

    /** 已通过认证的 admin */
    private User verifiedAdmin() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setEmail("admin@campus.com");
        u.setPhone("13800000000");
        u.setStudentNo("202012345678");
        u.setRealName("张三");
        u.setIsStudentVerified(1);
        return u;
    }

    /** 未认证的普通用户；isStudentVerified 为 null 是历史脏数据，要按未认证处理 */
    private User unverifiedBuyer() {
        User u = new User();
        u.setId(2L);
        u.setUsername("buyer1");
        u.setIsStudentVerified(null);
        return u;
    }

    private void loginAs(User u) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(u.getUsername());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(u));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(ResponseEntity<ApiResponse<?>> resp) {
        ApiResponse<?> api = resp.getBody();
        assertThat(api).as("响应体不应为空").isNotNull();
        return (Map<String, Object>) api.getData();
    }

    /* ==================== /api/user/profile ==================== */

    @Test
    @DisplayName("/profile 返回 studentVerified——修复刷新后徽章消失")
    void profileExposesStudentVerified() {
        loginAs(verifiedAdmin());

        Map<String, Object> data = dataOf(controller.getProfile(AUTH));

        assertThat(data).containsKey("studentVerified");
        assertThat(data.get("studentVerified")).isEqualTo(true);
        // 旧键名不得残留，否则前端会读到 undefined
        assertThat(data).doesNotContainKey("isStudentVerified");
        assertThat(data).doesNotContainKey("verified");
    }

    @Test
    @DisplayName("/profile 对未认证用户返回 false，而不是缺字段")
    void profileReturnsFalseForUnverified() {
        loginAs(unverifiedBuyer());

        Map<String, Object> data = dataOf(controller.getProfile(AUTH));

        assertThat(data).containsKey("studentVerified");
        assertThat(data.get("studentVerified")).isEqualTo(false);
    }

    @Test
    @DisplayName("/profile 的 studentVerified 是布尔，不是 0/1")
    void profileReturnsRealBooleanNotInteger() {
        loginAs(verifiedAdmin());

        Object flag = dataOf(controller.getProfile(AUTH)).get("studentVerified");

        // 前端写的是 === true，Integer 1 会静默变成未认证
        assertThat(flag).isInstanceOf(Boolean.class);
        assertThat((Boolean) flag).isTrue();
    }

    @Test
    @DisplayName("/profile 返回注册时间——资料页据此展示真实加入时间")
    void profileExposesCreatedTime() {
        User user = verifiedAdmin();
        LocalDateTime createdTime = LocalDateTime.of(2026, 11, 20, 9, 30);
        user.setCreatedTime(createdTime);
        loginAs(user);

        Map<String, Object> data = dataOf(controller.getProfile(AUTH));

        assertThat(data).containsKey("createdTime");
        assertThat(data.get("createdTime")).isEqualTo(createdTime);
    }

    @Test
    @DisplayName("/profile 对缺失 createdTime 的旧数据返回 null，不编造日期")
    void profileHandlesMissingCreatedTimeSafely() {
        User user = unverifiedBuyer();
        user.setCreatedTime(null);
        loginAs(user);

        Map<String, Object> data = dataOf(controller.getProfile(AUTH));

        // 前端 formatJoinDate(null) 返回空串并隐藏整行，不能回退到 2025 年
        assertThat(data).containsKey("createdTime");
        assertThat(data.get("createdTime")).isNull();
    }

    @Test
    @DisplayName("/profile 不允许修改用户名——JWT 以用户名为 subject")
    void updateProfileRejectsUsernameChange() {
        loginAs(verifiedAdmin());

        ApiResponse<?> api = controller.updateProfile(AUTH, Map.of("username", "newName"))
                .getBody();

        assertThat(api).isNotNull();
        assertThat(api.getCode()).isEqualTo(400);
        verify(userRepository, never()).updateById(any(User.class));
    }

    /* ==================== /api/user/hall-profile ==================== */

    @Test
    @DisplayName("/hall-profile 的 verified 改名为 studentVerified")
    void hallProfileUsesStudentVerified() {
        loginAs(verifiedAdmin());
        when(postService.countStats(anyLong())).thenReturn(Map.of());

        Map<String, Object> data = dataOf(controller.hallProfile(AUTH));

        assertThat(data).containsKey("studentVerified");
        assertThat(data.get("studentVerified")).isEqualTo(true);
        assertThat(data).doesNotContainKey("verified");
    }

    /* ==================== /api/user/user/{id} ==================== */

    @Test
    @DisplayName("/user/{id} 的 isStudentVerified 改名为 studentVerified")
    void publicProfileUsesStudentVerified() {
        when(userRepository.selectById(1L)).thenReturn(verifiedAdmin());

        Map<String, Object> data = dataOf(controller.getUserById(1L));

        assertThat(data).containsKey("studentVerified");
        assertThat(data.get("studentVerified")).isEqualTo(true);
        assertThat(data).doesNotContainKey("isStudentVerified");
    }

    @Test
    @DisplayName("/user/{id} 公开资料不得包含邮箱与手机号")
    void publicProfileDoesNotLeakContactInfo() {
        when(userRepository.selectById(1L)).thenReturn(verifiedAdmin());

        Map<String, Object> data = dataOf(controller.getUserById(1L));

        // 本接口无需登录，返回联系方式等于开放全站 PII 枚举
        assertThat(data).doesNotContainKeys("email", "phone");
        // 展示所需字段仍要保留
        assertThat(data).containsKeys("username", "avatar", "studentVerified");
    }

    /* ==================== /api/user/student-verify ==================== */

    @Test
    @DisplayName("/student-verify 的响应也用 studentVerified")
    void studentVerifyResponseUsesStudentVerified() {
        User alice = verifiedAdmin();
        loginAs(alice);
        when(studentVerifyService.verify(anyLong(), any(), any())).thenReturn(alice);

        Map<String, Object> data = dataOf(controller.studentVerify(AUTH,
                Map.of("studentNo", "202012345678", "realName", "张三")));

        assertThat(data).containsKey("studentVerified");
        assertThat(data.get("studentVerified")).isEqualTo(true);
        assertThat(data).doesNotContainKey("isStudentVerified");
    }

    @Test
    @DisplayName("四个返回点键名一致——这是前端只写一处判断的前提")
    void allFourEndpointsShareTheSameKeyName() {
        loginAs(verifiedAdmin());
        when(postService.countStats(anyLong())).thenReturn(Map.of());
        when(userRepository.selectById(1L)).thenReturn(verifiedAdmin());
        when(studentVerifyService.verify(anyLong(), any(), any())).thenReturn(verifiedAdmin());

        for (Map<String, Object> data : java.util.List.of(
                dataOf(controller.getProfile(AUTH)),
                dataOf(controller.hallProfile(AUTH)),
                dataOf(controller.getUserById(1L)),
                dataOf(controller.studentVerify(AUTH,
                        Map.of("studentNo", "202012345678", "realName", "张三"))))) {
            assertThat(data).containsKey("studentVerified");
            assertThat(data.keySet())
                    .doesNotContain("isStudentVerified", "verified", "studentVerifiedUser");
        }
    }
}
