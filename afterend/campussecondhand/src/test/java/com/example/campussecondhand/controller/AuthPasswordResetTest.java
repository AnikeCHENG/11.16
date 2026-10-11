package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.EmailService;
import com.example.campussecondhand.service.UserService;
import com.example.campussecondhand.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 忘记密码 / 重置密码的契约（P1）。
 *
 * <p><b>修复前是什么样：</b>{@code POST /auth/forgot-password} 只打日志、
 * 根本不发信，却在邮箱查不到时返回 {@code 404 邮箱不存在}。前半句让按钮点了没反应，
 * 后半句把接口变成了免费的账号枚举器——先拿字典跑一遍邮箱确认哪些已注册，
 * 再针对确认存在的账号做撞库或钓鱼。</p>
 *
 * <p><b>本测试钉死三件事：</b></p>
 * <ol>
 *   <li>存在与不存在的邮箱，响应体<b>逐字节相同</b>（用等值断言而非各测各的）；</li>
 *   <li>不存在的邮箱同样占用发送配额——否则"第二次请求 429"只对已注册邮箱成立，
 *       频控自己就成了新的探针；</li>
 *   <li>重置密码：验证码错、邮箱不存在、参数不全，都返回同一类 400，
 *       且失败路径绝不写库。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthPasswordResetTest {

    private static final String EMAIL = "student@campus.edu";

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private AuthController controller;

    private User user() {
        User u = new User();
        u.setId(7L);
        u.setUsername("buyer1");
        u.setEmail(EMAIL);
        u.setPassword("$2a$10$oldHashOldHashOldHashOldHashOldHashOldHashOldHash");
        return u;
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    private static String messageOf(ResponseEntity<ApiResponse<?>> resp) {
        return resp.getBody().getMessage();
    }

    // ---------------- 忘记密码：发送重置验证码 ----------------

    @Test
    @DisplayName("已注册邮箱：真的发出重置验证码")
    void forgot_password_sends_code_for_registered_email() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        var resp = controller.forgotPassword(Map.of("email", EMAIL));

        assertThat(codeOf(resp)).isEqualTo(200);
        verify(emailService).sendResetCode(EMAIL);
    }

    @Test
    @DisplayName("未注册邮箱：不发信，但同样占用发送配额（否则 429 就成了枚举信号）")
    void forgot_password_reserves_slot_even_when_email_is_unknown() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        controller.forgotPassword(Map.of("email", EMAIL));

        verify(emailService, never()).sendResetCode(anyString());
        verify(emailService).reserveSendSlot(EMAIL);
    }

    @Test
    @DisplayName("响应体逐字节相同：无法从文案区分邮箱是否注册")
    void forgot_password_reply_does_not_reveal_registration_status() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        var registered = controller.forgotPassword(Map.of("email", EMAIL));

        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        var unknown = controller.forgotPassword(Map.of("email", EMAIL));

        assertThat(messageOf(registered)).isEqualTo(messageOf(unknown));
        assertThat(codeOf(registered)).isEqualTo(codeOf(unknown)).isEqualTo(200);
    }

    @Test
    @DisplayName("频控：已注册与未注册邮箱返回完全一样的 429 文案")
    void forgot_password_throttle_reply_is_identical_for_both_branches() {
        String throttle = "60秒内只能发送一次";

        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        doThrow(new EmailService.SendTooFrequentException(throttle))
                .when(emailService).sendResetCode(EMAIL);
        var registered = controller.forgotPassword(Map.of("email", EMAIL));

        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        doThrow(new EmailService.SendTooFrequentException(throttle))
                .when(emailService).reserveSendSlot(EMAIL);
        var unknown = controller.forgotPassword(Map.of("email", EMAIL));

        assertThat(codeOf(registered)).isEqualTo(429);
        assertThat(codeOf(unknown)).isEqualTo(429);
        assertThat(messageOf(registered)).isEqualTo(messageOf(unknown)).isEqualTo(throttle);
    }

    @Test
    @DisplayName("邮件服务器故障：500，且不谎报成功")
    void forgot_password_reports_mail_failure() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        doThrow(new RuntimeException("Mail server unreachable")).when(emailService).sendResetCode(EMAIL);

        assertThat(codeOf(controller.forgotPassword(Map.of("email", EMAIL)))).isEqualTo(500);
    }

    @Test
    @DisplayName("邮箱为空：400")
    void forgot_password_rejects_blank_email() {
        assertThat(codeOf(controller.forgotPassword(Map.of("email", "  ")))).isEqualTo(400);
        verify(emailService, never()).sendResetCode(anyString());
    }

    // ---------------- 重置密码 ----------------

    @Test
    @DisplayName("验证码正确：写入 BCrypt 新密码，且不落明文")
    void reset_password_stores_encoded_password() throws Exception {
        User u = user();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("newPass123")).thenReturn("$2a$10$newHash");
        doNothing().when(emailService).verify(EMAIL, "123456");

        var resp = controller.resetPassword(
                Map.of("email", EMAIL, "code", "123456", "newPassword", "newPass123"));

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(u.getPassword()).isEqualTo("$2a$10$newHash");
        assertThat(u.getPassword()).doesNotContain("newPass123");
        verify(userService).updateUser(u);
    }

    @Test
    @DisplayName("验证码错误：400，且绝不改密码")
    void reset_password_rejects_wrong_code() {
        doThrow(new RuntimeException("验证码错误")).when(emailService).verify(EMAIL, "000000");

        var resp = controller.resetPassword(
                Map.of("email", EMAIL, "code", "000000", "newPassword", "newPass123"));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(userService, never()).updateUser(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("邮箱不存在：与验证码错误同一句提示（不泄露注册状态）")
    void reset_password_reply_does_not_reveal_registration_status() {
        doThrow(new RuntimeException("验证码不存在或已过期")).when(emailService).verify(EMAIL, "123456");
        var wrongCode = controller.resetPassword(
                Map.of("email", EMAIL, "code", "123456", "newPassword", "newPass123"));

        doNothing().when(emailService).verify(EMAIL, "123456");
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        var unknownEmail = controller.resetPassword(
                Map.of("email", EMAIL, "code", "123456", "newPassword", "newPass123"));

        assertThat(codeOf(wrongCode)).isEqualTo(400);
        assertThat(codeOf(unknownEmail)).isEqualTo(400);
        assertThat(messageOf(wrongCode)).isEqualTo(messageOf(unknownEmail));
    }

    @Test
    @DisplayName("新密码少于 6 位：400，且不消耗验证码")
    void reset_password_rejects_short_password() throws Exception {
        var resp = controller.resetPassword(
                Map.of("email", EMAIL, "code", "123456", "newPassword", "12345"));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(messageOf(resp)).contains("6");
        verify(emailService, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("字段缺失：400")
    void reset_password_rejects_missing_fields() {
        assertThat(codeOf(controller.resetPassword(Map.of("email", EMAIL)))).isEqualTo(400);
        assertThat(codeOf(controller.resetPassword(Map.of("email", EMAIL, "code", "123456"))))
                .isEqualTo(400);
        verify(userService, never()).updateUser(any(User.class));
    }
}
