package com.example.campussecondhand.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.entity.Post;
import com.example.campussecondhand.repository.PostRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 广场列表的物理分页（第二轮 N11）。
 *
 * <p><b>修复前：</b>{@code PostService.list(type, page, size)} 的 page/size
 * <b>在方法体里从未被使用</b>，内部是无条件 {@code selectList(wrapper)}，
 * Controller 再把结果 subList 出当页。每打开一次广场，就把整张 {@code posts} 表
 * 连同 enrich 的批量查询全部读出来，然后丢掉 99% 的行。</p>
 *
 * <p>现在 LIMIT 由分页拦截器拼进 SQL，只取当页。这里的判据是
 * "调的是 selectPage 而不是 selectList"——一旦有人图省事改回全表，
 * 这个断言会立刻红。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServicePageTest {

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

    private IPage<Post> emptyPage() {
        Page<Post> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());
        return page;
    }

    @Test
    @DisplayName("分页下推到 SQL：调 selectPage，绝不调 selectList")
    void pushes_limit_into_sql() {
        when(postRepository.selectPage(any(), any())).thenReturn(emptyPage());

        postService.page(null, PageParam.of(1, 10));

        verify(postRepository).selectPage(any(), any());
        verify(postRepository, never()).selectList(any());
    }

    @Test
    @DisplayName("排序仍然下推到 SQL（分页与排序必须同一条语句）")
    void keeps_ordering_in_sql() {
        when(postRepository.selectPage(any(), any())).thenReturn(emptyPage());

        postService.page("SELL", PageParam.of(1, 10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<Post>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(postRepository).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("type").contains("created_time");
    }

    @Test
    @DisplayName("非法 type：直接返回空页，一次库都不查（不静默降级为「全部」）")
    void invalid_type_short_circuits() {
        IPage<Post> result = postService.page("NOT_A_TYPE", PageParam.of(1, 10));

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("只 enrich 当页记录，不再为被丢掉的行做批量查询")
    void enriches_only_current_page() {
        Post p1 = new Post();
        p1.setId(1L);
        p1.setType("SELL");
        p1.setUserId(9L);
        Page<Post> page = new Page<>(1, 2, 1);
        page.setRecords(List.of(p1));
        when(postRepository.selectPage(any(), any())).thenReturn(page);
        when(userRepository.selectBatchIds(any())).thenReturn(List.of());

        IPage<Post> result = postService.page(null, PageParam.of(1, 2));

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        // 作者批量查询只针对当页的 userId
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).selectBatchIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(9L);
    }
}
