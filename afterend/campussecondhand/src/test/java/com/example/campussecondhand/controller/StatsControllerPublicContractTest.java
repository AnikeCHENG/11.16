package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.service.AdminStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 首页公开统计的响应契约。
 *
 * <p>这是匿名可读接口：不带任何 Authorization 也应返回 code=200，
 * 且 data 固定包含三个数字字段。前端 Hero 直接按这三个 key 取值，
 * 改名或改成需要登录都会让首页数字变成「—」。</p>
 */
@ExtendWith(MockitoExtension.class)
class StatsControllerPublicContractTest {

    @Mock
    private AdminStatsService adminStatsService;

    @InjectMocks
    private StatsController controller;

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> dataOf(ResponseEntity<ApiResponse<?>> resp) {
        return (Map<String, Long>) resp.getBody().getData();
    }

    @Test
    @DisplayName("公开统计无需登录：直接调用返回 200 与三个 data key")
    void publicStatsNeedsNoAuthAndReturnsThreeKeys() throws Exception {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("registeredUserCount", 10L);
        stats.put("onSaleProductCount", 20L);
        stats.put("completedOrderCount", 30L);
        when(adminStatsService.publicHomeStats()).thenReturn(stats);

        ResponseEntity<ApiResponse<?>> resp = controller.getPublicStats();

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(dataOf(resp)).containsOnlyKeys(
                "registeredUserCount", "onSaleProductCount", "completedOrderCount");
        assertThat(dataOf(resp)).containsAllEntriesOf(stats);

        // 公开接口不应要求 Authorization 参数
        Method method = StatsController.class.getMethod("getPublicStats");
        assertThat(method.getParameterCount()).isZero();
    }
}
