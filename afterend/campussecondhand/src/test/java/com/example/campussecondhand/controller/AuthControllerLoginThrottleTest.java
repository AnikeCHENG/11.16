package com.example.campussecondhand.controller;

import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.EmailService;
import com.example.campussecondhand.service.LoginAttemptService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录接口接入失败频控的契约。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerLoginThrottleTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private UserService userService;
    @Mock private LoginAttemptService loginAttemptService;
    @InjectMocks private AuthController controller;

    private User user() {
        User u = new User();
        u.setId(5L);
        u.setUsername("alice");
        u.setPassword("$2a$10$hash");
        u.setRole(0);
        u.setStatus(1);
        return u;
    }

    @Test
    @DisplayName("锁定期间返回 429，且不做密码比对")
    void lockedReturns429WithoutCheckingPassword() {
        when(loginAttemptService.lockedSeconds(anyString(), any())).thenReturn(600L);

        var resp = controller.login(Map.of("username", "alice", "password", "right"));

        assertThat(resp.getBody().getCode()).isEqualTo(429);
        assertThat(resp.getBody().getMessage()).contains("10 分钟");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(anyString(), any());
    }

    @Test
    @DisplayName("密码错误记一次失败")
    void wrongPasswordRecordsFailure() {
        when(userService.findByUsername("alice")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("bad", "$2a$10$hash")).thenReturn(false);

        var resp = controller.login(Map.of("username", "alice", "password", "bad"));

        assertThat(resp.getBody().getCode()).isEqualTo(401);
        verify(loginAttemptService).recordFailure(org.mockito.ArgumentMatchers.eq("alice"), any());
    }

    @Test
    @DisplayName("不存在的用户名同样记失败，避免锁定行为成为枚举信号")
    void unknownUserRecordsFailure() {
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        var resp = controller.login(Map.of("username", "ghost", "password", "x"));

        assertThat(resp.getBody().getCode()).isEqualTo(401);
        verify(loginAttemptService).recordFailure(org.mockito.ArgumentMatchers.eq("ghost"), any());
    }

    @Test
    @DisplayName("登录成功清零计数")
    void successClearsCounter() {
        when(userService.findByUsername("alice")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("right", "$2a$10$hash")).thenReturn(true);
        when(jwtUtil.generateToken("alice", 0)).thenReturn("tok");

        var resp = controller.login(Map.of("username", "alice", "password", "right"));

        assertThat(resp.getBody().getCode()).isEqualTo(200);
        verify(loginAttemptService).recordSuccess("alice");
    }
}
