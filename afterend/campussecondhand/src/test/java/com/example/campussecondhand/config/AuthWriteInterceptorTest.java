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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端写请求兜底鉴权（P1）。
 *
 * <p>背景：{@code SecurityConfig} 是 permitAll，写接口是否校验登录完全取决于
 * 每个 Controller 有没有手写解析逻辑。本拦截器把「写请求必须登录且未封禁」
 * 变成全局默认，测试覆盖放行与拒绝两侧，避免它本身变成摆设。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthWriteInterceptorTest {

    private static final String AUTH = "Bearer valid-token";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AuthWriteInterceptor interceptor;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(AUTH);
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody, true));
    }

    private User user(Integer status) {
        User u = new User();
        u.setId(1L);
        u.setUsername("buyer1");
        u.setRole(0);
        u.setStatus(status);
        return u;
    }

    private boolean preHandle() throws Exception {
        return interceptor.preHandle(request, response, new Object());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    @DisplayName("读请求与 CORS 预检：直接放行，不查 token")
    void allowsReadMethodsWithoutToken(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(preHandle()).isTrue();
        verify(userRepository, never()).findByUsername(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("带 token 的读请求：封禁账号 403，旧 token 读也立即失效")
    void rejectsBannedUserOnRead(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(0)));

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(403);
        assertThat(responseBody.toString()).contains("账户已被禁用");
    }

    @Test
    @DisplayName("带 token 的读请求：正常用户放行")
    void allowsActiveUserOnRead() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(1)));

        assertThat(preHandle()).isTrue();
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("读请求带无效 token：放行，由 Controller 决定是否 401（公开读接口不受影响）")
    void allowsReadWithInvalidToken() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn(null);

        assertThat(preHandle()).isTrue();
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("OPTIONS 预检：即使带 token 也不查库")
    void optionsNeverLooksUpUser() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertThat(preHandle()).isTrue();
        verify(userRepository, never()).findByUsername(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("写请求没有 Authorization：401")
    void rejectsWriteWithoutHeader(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(401);
        assertThat(responseBody.toString()).contains("未登录或登录已过期");
    }

    @Test
    @DisplayName("Authorization 不是 Bearer 格式：401")
    void rejectsMalformedHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc");

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("Bearer 后为空 token：401")
    void rejectsBlankToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer   ");

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("token 无效或过期：401")
    void rejectsInvalidToken() throws Exception {
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn(null);

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(401);
        assertThat(responseBody.toString()).contains("登录已过期");
    }

    @Test
    @DisplayName("token 有效但用户已被删除：401")
    void rejectsDeletedUser() throws Exception {
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.empty());

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(401);
        assertThat(responseBody.toString()).contains("用户不存在");
    }

    @Test
    @DisplayName("账号被封禁（status=0）：403，旧 token 立即失效")
    void rejectsBannedUser() throws Exception {
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(0)));

        assertThat(preHandle()).isFalse();
        verify(response).setStatus(403);
        assertThat(responseBody.toString()).contains("账户已被禁用");
    }

    @Test
    @DisplayName("正常用户：放行")
    void allowsActiveUser() throws Exception {
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(1)));

        assertThat(preHandle()).isTrue();
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("status 为 null 的历史数据：视为正常，不误伤")
    void allowsNullStatus() throws Exception {
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(null)));

        assertThat(preHandle()).isTrue();
    }
}
