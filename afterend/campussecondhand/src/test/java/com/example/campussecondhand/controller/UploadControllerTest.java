package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.UploadService;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传接口的鉴权与错误映射（P1-12）。
 *
 * <p>{@code SecurityConfig} 是 {@code anyRequest().permitAll()}，拦截器只管
 * {@code /api/admin/**}，所以这个端点的"必须登录"只能自己写——漏掉就是
 * "任何人可往服务器磁盘写文件"。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Mock
    private UploadService uploadService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private UploadController controller;

    private final MockMultipartFile file =
            new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3, 4});

    private void tokenResolvesTo(Long userId) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("user" + userId);
        User u = new User();
        u.setId(userId);
        u.setUsername("user" + userId);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(u));
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @Test
    @DisplayName("未登录：401，绝不写磁盘")
    void requires_login() throws Exception {
        assertThat(codeOf(controller.uploadImage(null, file))).isEqualTo(401);
        verify(uploadService, never()).storeImage(any());
    }

    @Test
    @DisplayName("无效 token：401")
    void rejects_invalid_token() throws Exception {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(null);

        assertThat(codeOf(controller.uploadImage(AUTH, file))).isEqualTo(401);
        verify(uploadService, never()).storeImage(any());
    }

    @Test
    @DisplayName("上传成功：返回可直接存库的 url")
    @SuppressWarnings("unchecked")
    void returns_url_on_success() throws Exception {
        tokenResolvesTo(2L);
        when(uploadService.storeImage(file)).thenReturn("/uploads/2026/10/abc123.png");

        var resp = controller.uploadImage(AUTH, file);

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat((Map<String, Object>) resp.getBody().getData())
                .containsEntry("url", "/uploads/2026/10/abc123.png");
    }

    @Test
    @DisplayName("业务校验失败：400 带原因")
    void maps_bad_request_to_400() throws Exception {
        tokenResolvesTo(2L);
        when(uploadService.storeImage(file))
                .thenThrow(new BadRequestException("只支持 JPG / PNG / GIF / WebP 格式的图片"));

        var resp = controller.uploadImage(AUTH, file);

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("JPG");
    }

    @Test
    @DisplayName("服务端异常：500 且不回显底层消息（磁盘路径不外泄）")
    void does_not_leak_internal_error_detail() throws Exception {
        tokenResolvesTo(2L);
        when(uploadService.storeImage(file))
                .thenThrow(new IllegalStateException("No space left on device: E:\\uploads"));

        var resp = controller.uploadImage(AUTH, file);

        assertThat(codeOf(resp)).isEqualTo(500);
        assertThat(resp.getBody().getMessage())
                .doesNotContain("No space")
                .doesNotContain("uploads");
    }
}
