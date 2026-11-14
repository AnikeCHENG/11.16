package com.example.campussecondhand.service;

import com.example.campussecondhand.repository.DashboardRepository;
import com.example.campussecondhand.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 广场侧栏数据（热门话题 / 活跃用户）。
 *
 * <p>前端 {@code api/plaza.js} 一直请求这两个接口，后端此前没有实现，侧栏长期 404。</p>
 */
@ExtendWith(MockitoExtension.class)
class PlazaServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private DashboardRepository dashboardRepository;
    @InjectMocks
    private PlazaService plazaService;

    @Test
    @DisplayName("热门话题：拆开逗号分隔的标签并按次数排序")
    void hot_topics_splits_and_counts_tags() {
        when(postRepository.findRecentTags(500)).thenReturn(Arrays.asList(
                "考研,高数",
                "考研",
                "闲置,考研,教材",
                null,
                "   "));

        var topics = plazaService.hotTopics(10);

        assertThat(topics).hasSize(4);
        assertThat(topics.get(0)).containsEntry("tag", "考研").containsEntry("count", 3);
        // 次数相同按话题名排序（String.compareTo，即码位序）：教材 < 闲置 < 高数
        assertThat(topics.get(1)).containsEntry("tag", "教材");
        assertThat(topics.get(2)).containsEntry("tag", "闲置");
        assertThat(topics.get(3)).containsEntry("tag", "高数");
        assertThat(topics).allSatisfy(t -> assertThat(t).containsKeys("tag", "count"));
    }

    @Test
    @DisplayName("热门话题：没有标签时返回空列表而不是报错")
    void hot_topics_empty_when_no_tags() {
        when(postRepository.findRecentTags(500)).thenReturn(List.of());

        assertThat(plazaService.hotTopics(10)).isEmpty();
    }

    @Test
    @DisplayName("热门话题：超出 limit 时按次数截断")
    void hot_topics_respects_limit() {
        when(postRepository.findRecentTags(500)).thenReturn(List.of("a", "b", "c"));

        assertThat(plazaService.hotTopics(2)).hasSize(2);
    }

    @Test
    @DisplayName("活跃用户：id 转成 userId，并给出副标题")
    void active_users_map_fields_for_frontend() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("username", "alice");
        row.put("avatar", null);
        row.put("lastActiveTime", "2026-11-11 20:15:00");
        when(dashboardRepository.activeUsers(6)).thenReturn(List.of(row));

        var users = plazaService.activeUsers(6);

        assertThat(users).hasSize(1);
        assertThat(users.get(0)).containsEntry("userId", 7L);
        assertThat(users.get(0)).containsEntry("username", "alice");
        assertThat(users.get(0)).containsEntry("subtitle", "2026-11-11 20:15");
        assertThat(users.get(0)).containsKey("avatar");
        verify(dashboardRepository).activeUsers(6);
    }

    @Test
    @DisplayName("活跃用户：仓储返回 null 时给出空列表")
    void active_users_null_safe() {
        when(dashboardRepository.activeUsers(6)).thenReturn(null);

        assertThat(plazaService.activeUsers(6)).isEmpty();
    }
}
