package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.service.PlazaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 广场侧栏接口：热门话题、活跃用户。
 *
 * <p>前端 {@code api/plaza.js} 一直在调用这两个地址，但后端一直没有实现，
 * 侧栏长期 404，只显示「暂时无法加载热门话题 / 活跃用户」。这里按前端契约补齐：
 * {@code data.topics} = [{tag, count}]，{@code data.users} = [{userId, username, avatar, subtitle}]。</p>
 */
@RestController
@RequestMapping("/api/plaza")
// 与其他控制器一致；Android App（http://localhost）跨域访问大厅接口需要
@CrossOrigin(origins = "*")
public class PlazaController {

    private static final Logger log = LoggerFactory.getLogger(PlazaController.class);

    @Autowired
    private PlazaService plazaService;

    @GetMapping("/hot-topics")
    public ResponseEntity<ApiResponse<?>> hotTopics() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("topics", plazaService.hotTopics(PlazaService.HOT_TOPICS_LIMIT));
            return ResponseEntity.ok(ApiResponse.success("获取成功", data));
        } catch (Exception e) {
            log.error("获取热门话题失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取热门话题失败"));
        }
    }

    @GetMapping("/active-users")
    public ResponseEntity<ApiResponse<?>> activeUsers() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users", plazaService.activeUsers(PlazaService.ACTIVE_USERS_LIMIT));
            return ResponseEntity.ok(ApiResponse.success("获取成功", data));
        } catch (Exception e) {
            log.error("获取活跃用户失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取活跃用户失败"));
        }
    }
}
