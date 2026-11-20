package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.service.AdminStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 公开统计接口。
 *
 * <p>首页 Hero 的三个数字来自真实数据库计数，不需要登录即可读取。
 * {@code SecurityConfig} 已是 {@code anyRequest().permitAll()}，
 * {@code AuthWriteInterceptor} 只拦截 POST/PUT/PATCH/DELETE，GET 本身放行，
 * 因此这里无需任何安全配置改动，也不接收 {@code Authorization} 参数。</p>
 */
@RestController
@RequestMapping("/api/stats")
@CrossOrigin(origins = "*")
public class StatsController {

    private final AdminStatsService adminStatsService;

    @Autowired
    public StatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<?>> getPublicStats() {
        return ResponseEntity.ok(ApiResponse.success("获取成功", adminStatsService.publicHomeStats()));
    }
}
