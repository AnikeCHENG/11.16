package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.MessageService;
import com.example.campussecondhand.service.ProductService;
import com.example.campussecondhand.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标记已读接口的越权回归（P2）。
 *
 * <p>Service 已把 {@code receiver_id} 写进 UPDATE，但前提是 Controller
 * 真的把「当前登录用户」传下去——如果仍然只传消息 id，SQL 层的防护等于没启用。
 * 这里用 mock 直接断言调用参数。</p>
 */
@ExtendWith(MockitoExtension.class)
class MessageControllerMarkReadTest {

    @Mock
    private MessageService messageService;
    @Mock
    private ProductService productService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private MessageController controller;

    private User user(long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setStatus(1);
        u.setRole(0);
        return u;
    }

    @Test
    @DisplayName("标记已读：传给 Service 的是当前登录用户 id，而不是路径里的 id")
    void markAsReadUsesAuthenticatedUser() {
        when(jwtUtil.getUsernameFromToken("token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(7L, "buyer1")));
        when(messageService.markAsRead(9L, 7L)).thenReturn(true);

        ResponseEntity<ApiResponse<?>> response = controller.markAsRead(9L, "Bearer token");

        verify(messageService).markAsRead(9L, 7L);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("不是收件人：Service 返回 false → 404，且文案不区分存在与否")
    void markAsReadOfOthersMessageReturns404() {
        when(jwtUtil.getUsernameFromToken("token")).thenReturn("buyer1");
        when(userRepository.findByUsername("buyer1")).thenReturn(Optional.of(user(7L, "buyer1")));
        when(messageService.markAsRead(9L, 7L)).thenReturn(false);

        ResponseEntity<ApiResponse<?>> response = controller.markAsRead(9L, "Bearer token");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).contains("无权操作");
    }
}
