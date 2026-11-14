package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.service.PlazaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 广场侧栏接口的响应契约。
 *
 * <p>前端按 {@code data.topics} / {@code data.users} 取值，字段名变了页面就空着，
 * 所以这里把契约钉死。</p>
 */
@ExtendWith(MockitoExtension.class)
class PlazaControllerContractTest {

    @Mock
    private PlazaService plazaService;
    @InjectMocks
    private PlazaController controller;

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(ResponseEntity<ApiResponse<?>> resp) {
        return (Map<String, Object>) resp.getBody().getData();
    }

    @Test
    @DisplayName("热门话题：返回 data.topics = [{tag, count}]")
    void hot_topics_returns_topics_under_data() {
        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("tag", "考研");
        topic.put("count", 3);
        when(plazaService.hotTopics(PlazaService.HOT_TOPICS_LIMIT)).thenReturn(List.of(topic));

        var resp = controller.hotTopics();

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(dataOf(resp)).containsKey("topics");
        assertThat((List<?>) dataOf(resp).get("topics")).hasSize(1);
    }

    @Test
    @DisplayName("活跃用户：返回 data.users = [{userId, username, avatar, subtitle}]")
    void active_users_returns_users_under_data() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", 7L);
        user.put("username", "alice");
        user.put("avatar", null);
        user.put("subtitle", "11-11 20:15");
        when(plazaService.activeUsers(PlazaService.ACTIVE_USERS_LIMIT)).thenReturn(List.of(user));

        var resp = controller.activeUsers();

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(dataOf(resp)).containsKey("users");
        assertThat((List<?>) dataOf(resp).get("users")).hasSize(1);
    }

    @Test
    @DisplayName("热门话题：异常时返回业务码 500，不抛到容器")
    void hot_topics_handles_exception() {
        when(plazaService.hotTopics(PlazaService.HOT_TOPICS_LIMIT)).thenThrow(new RuntimeException("boom"));

        var resp = controller.hotTopics();

        assertThat(codeOf(resp)).isEqualTo(500);
    }

    @Test
    @DisplayName("活跃用户：异常时返回业务码 500，不抛到容器")
    void active_users_handles_exception() {
        when(plazaService.activeUsers(PlazaService.ACTIVE_USERS_LIMIT)).thenThrow(new RuntimeException("boom"));

        var resp = controller.activeUsers();

        assertThat(codeOf(resp)).isEqualTo(500);
    }
}
