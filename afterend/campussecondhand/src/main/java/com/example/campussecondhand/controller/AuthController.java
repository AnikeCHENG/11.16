package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.dto.UserDTO;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.service.EmailService;
import com.example.campussecondhand.service.UserService;
import com.example.campussecondhand.util.JwtUtil;
import com.example.campussecondhand.util.StudentNoMasker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // 登录
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody Map<String, String> credentials) {
        try {
            String username = credentials.get("username");
            String password = credentials.get("password");
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.ok(ApiResponse.error(400, "用户名和密码不能为空"));
            }
            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!passwordEncoder.matches(password, user.getPassword())) {
                    return ResponseEntity.ok(ApiResponse.error(401, "用户名或密码错误"));
                }
                if (user.getStatus() == 0) {
                    return ResponseEntity.ok(ApiResponse.error(403, "账户已被禁用"));
                }

                // 确保用户对象有 role 字段
                if (user.getRole() == null) {
                    user.setRole(0); // 默认角色为普通用户
                    userService.updateUser(user);
                }

                String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
                // 这里原来直接回 User 实体，前端登录后把整份 JSON 存进 localStorage，
                // 于是 email / phone / qq / wechat / bio / location 一起被落盘，
                // 且 isStudentVerified 是 Integer 0/1——与 /api/auth/me 的
                // studentVerified（布尔）不是同一个约定，前端 store 得同时认两种。
                // 改为显式返回消费方真正需要的四个字段加认证状态。
                // 用 LinkedHashMap 而非 Map.of：avatar 可能为 null，而 Map.of 不接受 null 值。
                Map<String, Object> loginUser = new LinkedHashMap<>();
                loginUser.put("id", user.getId());
                loginUser.put("username", user.getUsername());
                loginUser.put("role", user.getRole());
                loginUser.put("avatar", user.getAvatar());
                loginUser.put("studentVerified", user.isStudentVerifiedUser());
                Map<String, Object> loginResult = new LinkedHashMap<>();
                loginResult.put("token", token);
                loginResult.put("user", loginUser);
                return ResponseEntity.ok(ApiResponse.success("登录成功", loginResult));
            } else {
                return ResponseEntity.ok(ApiResponse.error(401, "用户名或密码错误"));
            }
        } catch (Exception e) {
            // 添加详细的日志记录
            log.error("登录失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "登录失败"));
        }
    }




    // 发送邮箱验证码
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<?>> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "邮箱不能为空"));
        }
        try {
            emailService.sendCode(email);
            return ResponseEntity.ok(ApiResponse.success("验证码已发送", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(429, e.getMessage()));
        }
    }

    // 注册
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody UserDTO userDTO) {
        try {
            if (userDTO.getCode() == null || userDTO.getCode().isBlank()) {
                return ResponseEntity.ok(ApiResponse.error(400, "验证码不能为空"));
            }
            try {
                emailService.verify(userDTO.getEmail(), userDTO.getCode());
            } catch (Exception e) {
                return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
            }

            if (userService.existsByUsername(userDTO.getUsername())) {
                return ResponseEntity.ok(ApiResponse.error(70, "用户名已存在"));
            }

            if (userService.existsByEmail(userDTO.getEmail())) {
                return ResponseEntity.ok(ApiResponse.error(70, "邮箱已被注册"));
            }

            User user = userService.registerUser(userDTO);
            log.info("注册成功: username={}", userDTO.getUsername());
            return ResponseEntity.ok(ApiResponse.success("注册成功", user));
        } catch (Exception e) {
            // 必须打印堆栈：否则注册失败在日志里没有任何痕迹，无法定位
            log.error("注册失败: username={}, email={}, 原因={}",
                    userDTO.getUsername(), userDTO.getEmail(), e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error(500, "注册失败"));
        }
    }


    /**
     * 当前登录用户身份。
     *
     * <p>供前端路由守卫判定管理员身份使用。刻意设计得足够轻量：
     * 只从 <b>JWT</b> 解析用户名与角色，不查询任何业务表、不做聚合运算——
     * 否则每次进入后台路由都要跑一遍统计查询。</p>
     *
     * <p>注意：这里返回的 role 来自服务端签名的 JWT，前端无法伪造。
     * 前端守卫本身只是体验优化（拦截误入），真实授权由
     * {@code AdminAuthInterceptor} 对 {@code /api/admin/**} 强制拦截。</p>
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> me(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录"));
            }
            String username = jwtUtil.getUsernameFromToken(authHeader.substring(7));
            if (username == null) {
                return ResponseEntity.ok(ApiResponse.error(401, "登录已过期，请重新登录"));
            }
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
            }
            User user = userOpt.get();
            Map<String, Object> me = new HashMap<>();
            me.put("id", user.getId());
            me.put("username", user.getUsername());
            me.put("role", user.getRole());
            me.put("avatar", user.getAvatar());
            me.put("status", user.getStatus());
            // 认证状态放进 /me，是因为它现在是全站身份信息的唯一入口：
            // 前端路由守卫对任何 requiresAuth 页面都会先 fetchMe() 一次，
            // 导航栏与个人中心可以直接读这里，不必各自再查一次 /api/users/{id}。
            me.put("studentVerified", user.isStudentVerifiedUser());
            // 学号是准身份标识，User.studentNo 标了 WRITE_ONLY 不参与序列化；
            // 这里只回本人可见的脱敏值，完整学号不进任何 JSON。
            me.put("studentNoMasked", StudentNoMasker.mask(user.getStudentNo()));
            return ResponseEntity.ok(ApiResponse.success("获取成功", me));
        } catch (Exception e) {
            log.warn("解析当前用户身份失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(401, "登录已过期，请重新登录"));
        }
    }

    /** 忘记密码的统一响应话术：存在与否都返回这一句，避免账号枚举 */
    private static final String FORGOT_PASSWORD_GENERIC_REPLY =
            "如果该邮箱已注册，验证码已发送，请查收";

    /**
     * 忘记密码 —— 发送重置验证码。
     *
     * <p><b>此前这是个空壳，而且是个会说话的壳：</b>方法体只打日志、根本没有发信，
     * 却在邮箱查不到时返回 {@code 404 邮箱不存在}。前者让"忘记密码"按钮点了没反应，
     * 后者等于免费提供一个账号枚举接口——先拿字典跑一遍邮箱确认哪些已注册，
     * 再对确认存在的账号做撞库或钓鱼。</p>
     *
     * <p><b>现在的两条约束：</b></p>
     * <ol>
     *   <li>响应不区分邮箱是否存在，一律返回同一句
     *       {@link #FORGOT_PASSWORD_GENERIC_REPLY}；</li>
     *   <li>频控<b>先于</b>存在性判断执行，并且对不存在的邮箱同样占配额——
     *       否则"第二次请求返回 429"只对已注册邮箱成立，频控又变成新的探针。</li>
     * </ol>
     *
     * <p>验证码复用 {@code EmailService}：60 秒一次、每日 10 封、5 分钟有效、
     * 错误 5 次作废，与注册验证码同一套规则。</p>
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<?>> forgotPassword(
            @RequestBody(required = false) Map<String, String> body) {
        String email = body == null ? null : body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "邮箱不能为空"));
        }
        email = email.trim();

        boolean registered = userService.findByEmail(email).isPresent();
        try {
            if (registered) {
                emailService.sendResetCode(email);
            } else {
                // 不存在的邮箱也要占掉这次配额，保证两条分支的 429 行为完全一致
                emailService.reserveSendSlot(email);
                log.info("忘记密码：邮箱未注册，按统一话术返回: email={}", email);
            }
        } catch (EmailService.SendTooFrequentException e) {
            // 频控对存在/不存在的邮箱都生效，因此不构成枚举信号
            return ResponseEntity.ok(ApiResponse.error(429, e.getMessage()));
        } catch (Exception e) {
            // 只有"邮件服务器真的挂了"才会走到这里；此时不谎报成功，但日志要留全
            log.error("重置验证码发送失败: email={}", email, e);
            return ResponseEntity.ok(ApiResponse.error(500, "验证码发送失败，请稍后重试"));
        }

        return ResponseEntity.ok(ApiResponse.success(FORGOT_PASSWORD_GENERIC_REPLY, null));
    }

    /**
     * 重置密码 —— 校验邮箱验证码后写入新密码。
     *
     * <p>验证码由 {@code EmailService.verify} 校验：正确即立刻删除（一次性使用），
     * 错误累加次数、超过 5 次直接作废该验证码。</p>
     *
     * <p><b>注意一个已知局限：</b>本项目 JWT 是无状态的，重置密码<b>不会</b>
     * 让此前签发的 token 失效（没有 jti / 黑名单机制）。真要做"改密即踢下线"，
     * 需要引入 token 版本号或黑名单，属答辩"局限与展望"的内容。</p>
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<?>> resetPassword(
            @RequestBody(required = false) Map<String, String> body) {
        String email = body == null ? null : body.get("email");
        String code = body == null ? null : body.get("code");
        String newPassword = body == null ? null : body.get("newPassword");

        if (email == null || email.isBlank()
                || code == null || code.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "邮箱、验证码和新密码都不能为空"));
        }
        // 与注册保持同一强度：UserDTO 要求密码至少 6 位
        if (newPassword.length() < 6) {
            return ResponseEntity.ok(ApiResponse.error(400, "密码长度不能少于6位"));
        }
        email = email.trim();

        try {
            emailService.verify(email, code.trim());
        } catch (Exception e) {
            // 校验失败与"邮箱不存在"共用同一句提示，避免从错误文案反推账号是否存在
            log.info("重置密码：验证码校验失败 email={}, 原因={}", email, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(400, "验证码错误或已过期"));
        }

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "验证码错误或已过期"));
        }

        try {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userService.updateUser(user);
            log.info("密码已重置: userId={}", user.getId());
            return ResponseEntity.ok(ApiResponse.success("密码已重置，请用新密码登录", null));
        } catch (Exception e) {
            log.error("重置密码写入失败: email={}", email, e);
            return ResponseEntity.ok(ApiResponse.error(500, "重置失败，请稍后重试"));
        }
    }



}
