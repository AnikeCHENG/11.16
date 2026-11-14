package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.CreditService;
import com.example.campussecondhand.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 信用分查询。
 *
 * <p>只读：分数的变更全部由业务事件驱动（认证通过、完成交易、超时等），
 * 不提供任何能直接改分的接口——否则信用分就失去了"由行为决定"的意义。</p>
 */
@RestController
@RequestMapping("/api/credit")
@CrossOrigin(origins = "*")
public class CreditController {

    @Autowired
    private CreditService creditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // 与 OrderController#getUserFromToken 同样的写法；以后统一做 JWT 过滤器时一起收口
    private Optional<User> getUserFromToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Optional.empty();
            }
            String token = authHeader.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 我的信用分 + 最近变动记录 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> myCredit(
            @RequestHeader("Authorization") String authHeader) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        return ResponseEntity.ok(ApiResponse.success("获取成功",
                creditService.detailOf(userOpt.get().getId())));
    }

    /** 他人的信用摘要（公开，只含分数和等级），用于商品详情页的卖家展示 */
    @GetMapping("/user/{id}")
    public ResponseEntity<ApiResponse<?>> userCredit(@PathVariable Long id) {
        if (userRepository.selectById(id) == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "用户不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success("获取成功", creditService.summaryOf(id)));
    }
}
