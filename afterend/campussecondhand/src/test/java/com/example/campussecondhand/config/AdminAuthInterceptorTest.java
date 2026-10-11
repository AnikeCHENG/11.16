package com.example.campussecondhand.config;

import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端拦截器的鉴权判据（第二轮扫描 N2）。
 *
 * <p><b>修复前：</b>只读 JWT 里的 {@code role} claim，不查库。token 的 role 是
 * 签发那一刻的快照，于是"把某个管理员降权/封禁"这个动作，在他手里那张
 * 旧 token 过期之前（{@code jwt.expiration=86400}，整整 24 小时）完全不起作用——
 * 后台大门照样为他敞开。</p>
 *
 * <p>现在必须同时满足：token 有效、claim role=1、<b>数据库里 role 仍是 1 且未封禁</b>。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthInterceptorTest {

    private static final String AUTH = "Bearer admin-token";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AdminAuthInterceptor interceptor;

    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(AUTH);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
    }

    private User user(int role, int status) {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private boolean preHandle() throws Exception {
        return interceptor.preHandle(request, response, new Object());
    }

    @Test
    @DisplayName("合法管理员：放行")
    void allows_valid_admin() throws Exception {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("admin");
        when(jwtUtil.getRoleFromToken(anyString())).thenReturn(1);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1, 1)));

        assertThat(preHandle()).isTrue();
    }

    @Test
    @DisplayName("已被降权的管理员：旧 token 仍然有效，但数据库里 role 已不是 1 → 拒绝")
    void rejects_demoted_admin_even_with_valid_token() throws Exception {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("admin");
        when(jwtUtil.getRoleFromToken(anyString())).thenReturn(1);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(0, 1)));

        assertThat(preHandle()).isFalse();
    }

    @Test
    @DisplayName("已被封禁的管理员：立即失效，不再等 24 小时 token 过期")
    void rejects_banned_admin_even_with_valid_token() throws Exception {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("admin");
        when(jwtUtil.getRoleFromToken(anyString())).thenReturn(1);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1, 0)));

        assertThat(preHandle()).isFalse();
    }

    @Test
    @DisplayName("账号已不存在（被删）：拒绝")
    void rejects_deleted_account() throws Exception {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("admin");
        when(jwtUtil.getRoleFromToken(anyString())).thenReturn(1);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertThat(preHandle()).isFalse();
    }

    @Test
    @DisplayName("claim 里就不是管理员：连库都不用查")
    void rejects_non_admin_claim() throws Exception {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("buyer1");
        when(jwtUtil.getRoleFromToken(anyString())).thenReturn(0);

        assertThat(preHandle()).isFalse();
    }

    @Test
    @DisplayName("没有 Authorization 头：拒绝")
    void rejects_missing_header() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(preHandle()).isFalse();
    }

    @Test
    @DisplayName("OPTIONS 预检：直接放行，不参与鉴权")
    void allows_options_preflight() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertThat(preHandle()).isTrue();
    }
}
