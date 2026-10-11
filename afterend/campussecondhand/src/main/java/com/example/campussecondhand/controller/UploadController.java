package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.UploadService;
import com.example.campussecondhand.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * 图片上传（P1-12）。
 *
 * <p>返回的 {@code /uploads/yyyy/MM/xxx.jpg} 直接存进 {@code products.images} /
 * {@code posts.images} / {@code users.avatar} 即可，前端拿它当 {@code <img src>} 用。</p>
 *
 * <p><b>必须登录</b>：{@code SecurityConfig} 是 {@code anyRequest().permitAll()}，
 * 拦截器只管 {@code /api/admin/**}，所以这个端点的鉴权只能自己写——
 * 不加就是"任何人可往服务器磁盘写文件"。</p>
 */
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    @Autowired
    private UploadService uploadService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/image")
    public ResponseEntity<ApiResponse<?>> uploadImage(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {
        Optional<User> userOpt = resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }

        try {
            String url = uploadService.storeImage(file);
            return ResponseEntity.ok(ApiResponse.success("上传成功", Map.of("url", url)));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            // 详情只进日志：磁盘路径、IO 异常不该回给客户端
            log.error("图片上传失败: userId={}", userOpt.get().getId(), e);
            return ResponseEntity.ok(ApiResponse.error(500, "图片上传失败，请稍后重试"));
        }
    }

    /** 解析 Authorization 为当前用户；缺失或无效返回 empty */
    private Optional<User> resolveUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            String username = jwtUtil.getUsernameFromToken(authHeader.substring(7));
            if (username == null) {
                return Optional.empty();
            }
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
