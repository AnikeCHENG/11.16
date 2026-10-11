package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Post;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.PostRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 广场动态的服务端长度上限（第二轮 N12）。
 *
 * <p>controller 收的是裸 {@code Map}，没有 {@code @Size}；
 * {@code posts.content} 是 TEXT(64KB)、{@code images} 是 MEDIUMTEXT，
 * 而 {@code application.properties} 里的 50MB 限制只管 multipart，
 * <b>管不到 JSON 请求体</b>。唯一真正拦住超大内容的地方就是这里。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceLimitTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductService productService;
    @InjectMocks
    private PostService postService;

    private PostService.CreateCommand command(String content, List<String> images) {
        return new PostService.CreateCommand("CHAT", content, null, images,
                null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        // 默认 insert 成功
        org.mockito.Mockito.when(postRepository.insert(any(Post.class))).thenReturn(1);
    }

    @Test
    @DisplayName("超过 2000 字：400，不落库")
    void rejects_overlong_content() {
        String tooLong = "字".repeat(2001);

        assertThatThrownBy(() -> postService.create(1L, command(tooLong, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2000");
        verify(postRepository, never()).insert(any(Post.class));
    }

    @Test
    @DisplayName("刚好 2000 字：放行（边界不能误伤）")
    void allows_exactly_max_content() {
        String exactly = "字".repeat(2000);

        Post created = postService.create(1L, command(exactly, null));

        assertThat(created.getContent()).hasSize(2000);
        verify(postRepository).insert(any(Post.class));
    }

    @Test
    @DisplayName("超过 9 张图：400")
    void rejects_too_many_images() {
        List<String> images = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            images.add("data:image/jpeg;base64,AAAA");
        }

        assertThatThrownBy(() -> postService.create(1L, command("", images)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("9");
        verify(postRepository, never()).insert(any(Post.class));
    }

    @Test
    @DisplayName("空内容且无图：仍然拒绝（原有规则不许回退）")
    void still_rejects_empty_post() {
        assertThatThrownBy(() -> postService.create(1L, command("   ", List.of())))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).insert(any(Post.class));
    }
}
