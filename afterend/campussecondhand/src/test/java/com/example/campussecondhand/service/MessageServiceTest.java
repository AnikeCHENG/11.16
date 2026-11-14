package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Message;
import com.example.campussecondhand.repository.MessageRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 标记已读的越权回归（P2）。
 *
 * <p>修复前 Service 只按消息 id 更新，任何登录用户都能把别人的消息标成已读。
 * 现在 UPDATE 必须同时匹配 {@code receiver_id}，因此这里断言的是
 * 「userId 确实传到了 SQL 层」，而不是在 Java 里先查再判。</p>
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("标记已读：消息 id 与当前用户 id 一起下推到 SQL")
    void markAsReadScopesToReceiver() {
        when(messageRepository.markAsRead(9L, 1L)).thenReturn(1);

        assertThat(messageService.markAsRead(9L, 1L)).isTrue();

        verify(messageRepository).markAsRead(9L, 1L);
    }

    @Test
    @DisplayName("别人的消息：影响 0 行 → 返回 false，不泄露是否存在")
    void cannotMarkOthersMessage() {
        when(messageRepository.markAsRead(9L, 2L)).thenReturn(0);

        assertThat(messageService.markAsRead(9L, 2L)).isFalse();

        verify(messageRepository).markAsRead(9L, 2L);
    }

    @Test
    @DisplayName("参数缺失：直接拒绝，不碰数据库")
    void rejectsNullArguments() {
        assertThat(messageService.markAsRead(null, 1L)).isFalse();
        assertThat(messageService.markAsRead(9L, null)).isFalse();
        assertThat(messageService.markAsRead(null, null)).isFalse();

        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("全部已读：一条 UPDATE 搞定，不再逐条全字段更新")
    void markAllAsRead_uses_single_update() {
        when(messageRepository.markAllAsRead(1L)).thenReturn(3);

        assertThat(messageService.markAllAsRead(1L)).isTrue();

        // 只发一条批量 UPDATE：不再「查全部消息 -> 逐条 updateById」
        verify(messageRepository).markAllAsRead(1L);
        verify(messageRepository, never()).findByReceiverId(any());
        verify(messageRepository, never()).updateById(any(Message.class));
    }

    @Test
    @DisplayName("全部已读：没有未读消息时同样成立，且不查列表")
    void markAllAsRead_with_no_unread_message() {
        when(messageRepository.markAllAsRead(1L)).thenReturn(0);

        assertThat(messageService.markAllAsRead(1L)).isTrue();

        verify(messageRepository).markAllAsRead(1L);
        verify(messageRepository, never()).findByReceiverId(any());
    }

    @Test
    @DisplayName("全部已读：用户 id 缺失直接拒绝")
    void markAllAsRead_rejects_null_user() {
        assertThat(messageService.markAllAsRead(null)).isFalse();

        verifyNoInteractions(messageRepository);
    }
}
