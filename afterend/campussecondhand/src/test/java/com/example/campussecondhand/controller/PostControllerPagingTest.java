package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.common.PageResult;
import com.example.campussecondhand.entity.Post;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.PostRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.PostService;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 广场列表接口的分页契约（第二轮 N11）。
 *
 * <p>判据是"接口报的 total 来自 SQL 的 count，list 只含当页记录"。
 * 此前 Controller 拿到的是一份全量列表，自己 {@code subList} 出当页、
 * 用 {@code all.size()} 当 total —— 数据量一大，接口慢在"把整张表读出来"上，
 * 而不是慢在返回那 10 条上。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostControllerPagingTest {

    @Mock
    private PostService postService;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private PostController controller;

    private IPage<Post> pageOf(int size, int total) {
        List<Post> records = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Post p = new Post();
            p.setId((long) (i + 1));
            p.setType("SELL");
            records.add(p);
        }
        Page<Post> page = new Page<>(1, size, total);
        page.setRecords(records);
        return page;
    }

    @SuppressWarnings("unchecked")
    private PageResult<Post> dataOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return (PageResult<Post>) resp.getBody().getData();
    }

    @Test
    @DisplayName("只返回当页记录，total 来自 SQL 的 count")
    void returns_current_page_and_sql_total() {
        when(postService.page(any(), any())).thenReturn(pageOf(10, 137));

        PageResult<Post> data = dataOf(controller.list(null, 1, 10));

        assertThat(data.list()).hasSize(10);
        assertThat(data.total()).isEqualTo(137);
        assertThat(data.page()).isEqualTo(1);
        assertThat(data.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("非法 type：空列表 + total=0，交给前端隐藏筛选态")
    void invalid_type_returns_empty_page() {
        when(postService.page(any(), any())).thenReturn(pageOf(0, 0));

        PageResult<Post> data = dataOf(controller.list("NOT_A_TYPE", 1, 10));

        assertThat(data.list()).isEmpty();
        assertThat(data.total()).isZero();
    }

    @Test
    @DisplayName("size 越界由 PageParam 统一抛 BadRequestException（全局处理器转 400）")
    void oversized_page_size_is_rejected() {
        assertThatThrownBy(() -> controller.list(null, 1, 100000))
                .isInstanceOf(BadRequestException.class);
    }
}
