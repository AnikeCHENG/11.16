package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.StudentVerification;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.service.ReviewService;
import com.example.campussecondhand.service.PostService;
import com.example.campussecondhand.service.StudentVerifyService;
import com.example.campussecondhand.service.StudentVerificationService;
import com.example.campussecondhand.service.UploadService;
import java.util.LinkedHashMap;
import com.example.campussecondhand.repository.AccountRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
/**
 * 用户资料与学生认证相关接口。
 *
 * <p><b>认证状态字段名统一约定：{@code studentVerified}（布尔）。</b></p>
 *
 * <p>这个字段此前在全站有四种写法——本控制器的 {@code /hall-profile} 叫
 * {@code verified}、{@code /profile} 干脆没返回、{@code /student-verify} 与
 * {@code /user/{id}} 叫 {@code isStudentVerified}，而
 * {@code MessageService} 与 {@code OrderService} 又叫 {@code studentVerified}。
 * 名字一散，每加一处消费点就得先猜对端叫什么，于是真的出了 bug：
 * Profile.vue 读 {@code data.isStudentVerified}，而 {@code /profile} 返回的是
 * {@code undefined}，于是认证成功的用户一刷新页面，徽章就消失、认证表单重新出现。</p>
 *
 * <p>前端一律读 {@code studentVerified}。新增返回点时请沿用这个名字，
 * 不要因为实体字段叫 {@code isStudentVerified} 就顺手沿用——那正是
 * {@code studentVerifiedUser} 这种怪名字的由来。</p>
 */
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StudentVerifyService studentVerifyService;

    @Autowired
    private StudentVerificationService studentVerificationService;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private ReviewService reviewService;

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private PostService postService;

    /** 余额走原生 SQL（users.balance 不在 User 实体里），仅本人资料接口读取 */
    @Autowired
    private AccountRepository accountRepository;

    /**
     * 大厅左侧用户名片：昵称 / 头像 / 认证 / 年级 / 三条统计，一次接口返回。
     *
     * <p>nickname 字段：users 表当前没有该列，按裁决以 username 兜底
     * （「昵称可编辑」已记入答辩后 TODO）。</p>
     *
     * <p>grade：从学号解析入学年份，如 {@code 202012345678 → "2020级"}。
     * 学号为空或格式不匹配时返回 {@code null}，前端据此隐藏年级区域，
     * 绝不用「未知年级」之类的占位文案冒充真实数据。</p>
     */
    @GetMapping("/hall-profile")
    public ResponseEntity<ApiResponse<?>> hallProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }
            User user = userOpt.get();

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("nickname", user.getUsername());
            profile.put("avatar", user.getAvatar());
            profile.put("studentVerified", Integer.valueOf(1).equals(user.getIsStudentVerified()));
            profile.put("grade", parseGrade(user.getStudentNo()));
            profile.put("stats", postService.countStats(user.getId()));

            return ResponseEntity.ok(ApiResponse.success("获取成功", profile));
        } catch (Exception e) {
            log.error("获取大厅名片失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取名片失败"));
        }
    }

    /**
     * 从学号解析年级标签。
     *
     * <p>规则：取前 4 位，必须是 4 位纯数字且落在 1980~2100 之间，
     * 才认定为入学年份并拼出「{年份}级」。任一条件不满足返回 null。</p>
     */
    private static String parseGrade(String studentNo) {
        if (studentNo == null || studentNo.trim().length() < 4) {
            return null;
        }
        String prefix = studentNo.trim().substring(0, 4);
        if (!prefix.matches("\\d{4}")) {
            return null;
        }
        int year = Integer.parseInt(prefix);
        if (year < 1980 || year > 2100) {
            return null;
        }
        return year + "级";
    }

    private Optional<User> getUserFromToken(String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return Optional.empty();
            }
            String username = jwtUtil.getUsernameFromToken(token.substring(7));
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            log.error("获取用户信息失败: ", e);
            return Optional.empty();
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<?>> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }

            User user = userOpt.get();
            // 学号与真实姓名在 User 上是 WRITE_ONLY，序列化时不会自动带出。
            // 本接口是「本人查本人」，鉴权已保证不会越权，故显式补回这两个字段——
            // 否则前端 Profile 的认证信息区永远显示「未认证」。
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id", user.getId());
            profile.put("username", user.getUsername());
            profile.put("email", user.getEmail());
            profile.put("phone", user.getPhone() != null ? user.getPhone() : "");
            profile.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
            profile.put("bio", user.getBio() != null ? user.getBio() : "");
            profile.put("location", user.getLocation() != null ? user.getLocation() : "");
            profile.put("qq", user.getQq() != null ? user.getQq() : "");
            profile.put("wechat", user.getWechat() != null ? user.getWechat() : "");
            profile.put("studentNo", user.getStudentNo() != null ? user.getStudentNo() : "");
            profile.put("realName", user.getRealName() != null ? user.getRealName() : "");
            // 注册时间供资料页展示真实「加入于 YYYY年M月」；
            // 历史数据可能为 null，原样带出由前端自行隐藏，不在这里编造日期。
            profile.put("createdTime", user.getCreatedTime());
            // 认证状态此前只补了 studentNo / realName，漏了这个布尔字段，
            // 而 Profile.vue 读的是 data.isStudentVerified → undefined === true 恒为 false，
            // 于是认证成功后一刷新页面，徽章消失、认证表单重新出现，
            // 再提交就撞上后端「已通过学生认证」的 400。
            profile.put("studentVerified", user.isStudentVerifiedUser());
            // 余额是本人可见的资金数据：收银台据此展示「余额支付」可用额度
            java.math.BigDecimal balance = accountRepository.balanceOf(user.getId());
            profile.put("balance", balance != null ? balance : java.math.BigDecimal.ZERO);
            return ResponseEntity.ok(ApiResponse.success("获取成功", profile));
        } catch (Exception e) {
            log.error("获取用户资料失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取资料失败"));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<?>> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> profileData) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }

            User user = userOpt.get();

            if (profileData.containsKey("username")) {
                String username = profileData.get("username").trim();
                if (username.isEmpty()) {
                    return ResponseEntity.ok(ApiResponse.error(400, "用户名不能为空"));
                }
                if (username.length() < 2 || username.length() > 20) {
                    return ResponseEntity.ok(ApiResponse.error(400, "用户名需要在2-20个字符之间"));
                }
                // 用户名同时是登录凭证与 JWT 的 subject，改名会让现有 token 立即失效；
                // 更严重的是旧用户名被他人注册后，旧 token 会被解析成新用户。
                // 在 JWT 改为携带不可变 userId 之前，这里明确拒绝改名，而不是留下串号风险。
                if (!username.equals(user.getUsername())) {
                    return ResponseEntity.ok(ApiResponse.error(400, "用户名是登录凭证，暂不支持修改"));
                }
            }

            if (profileData.containsKey("avatar")) {
                // users.avatar 是 MEDIUMTEXT（Base64 Data URL），不限制长度等于
                // 让任意大的字符串进库；上传接口本身限制在 10MB 以内，这里再兜一层
                String avatar = profileData.get("avatar");
                if (avatar != null && avatar.length() > 2_000_000) {
                    return ResponseEntity.ok(ApiResponse.error(400, "头像数据过大，请重新上传"));
                }
                user.setAvatar(avatar);
            }

            if (profileData.containsKey("bio")) {
                String bio = profileData.get("bio");
                if (bio != null && bio.length() > 255) {
                    return ResponseEntity.ok(ApiResponse.error(400, "个人简介不能超过255个字符"));
                }
                user.setBio(bio);
            }

            if (profileData.containsKey("location")) {
                String location = profileData.get("location");
                if (location != null && location.length() > 100) {
                    return ResponseEntity.ok(ApiResponse.error(400, "所在位置不能超过100个字符"));
                }
                user.setLocation(location);
            }

            if (profileData.containsKey("qq")) {
                String qq = profileData.get("qq");
                if (qq != null && qq.length() > 20) {
                    return ResponseEntity.ok(ApiResponse.error(400, "QQ号不能超过20个字符"));
                }
                user.setQq(qq);
            }

            if (profileData.containsKey("wechat")) {
                String wechat = profileData.get("wechat");
                if (wechat != null && wechat.length() > 50) {
                    return ResponseEntity.ok(ApiResponse.error(400, "微信号不能超过50个字符"));
                }
                user.setWechat(wechat);
            }

            userRepository.updateById(user);

            return ResponseEntity.ok(ApiResponse.success("保存成功", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "phone", user.getPhone() != null ? user.getPhone() : "",
                    "avatar", user.getAvatar() != null ? user.getAvatar() : "",
                    "bio", user.getBio() != null ? user.getBio() : "",
                    "location", user.getLocation() != null ? user.getLocation() : "",
                    "qq", user.getQq() != null ? user.getQq() : "",
                    "wechat", user.getWechat() != null ? user.getWechat() : ""
            )));
        } catch (Exception e) {
            log.error("更新用户资料失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "保存失败"));
        }
    }

    /**
     * 学生认证。
     *
     * <p><b>当前为模拟校验，接口预留，未来可对接教务系统或改为人工审核。</b>
     * 校验规则见 {@link StudentVerifyService}。</p>
     */
    @PostMapping("/student-verify")
    public ResponseEntity<ApiResponse<?>> studentVerify(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }
            String studentNo = body.get("studentNo") == null ? null : String.valueOf(body.get("studentNo"));
            String realName = body.get("realName") == null ? null : String.valueOf(body.get("realName"));

            User user = studentVerifyService.verify(userOpt.get().getId(), studentNo, realName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("studentVerified", true);
            result.put("studentNo", user.getStudentNo());
            // 真实姓名回显给本人，方便确认填错没有
            result.put("realName", user.getRealName());
            return ResponseEntity.ok(ApiResponse.success("认证成功，已享受学生专属免手续费", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("学生认证失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "认证失败，请稍后重试"));
        }
    }

    /**
     * 提交「支付宝学生认证截图」辅助认证申请（进入待审核状态，由管理员人工审核）。
     *
     * <p>仅面向无法完成既有学生认证的用户。平台只收集支付宝官方"学生认证"
     * 结果截图与基本学籍信息，<b>不收集</b>支付宝账号、密码、支付密码、
     * 短信验证码或完整身份证号；截图中可对敏感信息打码。</p>
     *
     * <p>截图先经 {@code POST /api/upload/image} 落盘（魔术字节嗅探 + UUID 重命名），
     * 本接口只接收返回的 {@code /uploads/...} 路径。</p>
     */
    @PostMapping("/student-verification/screenshot")
    public ResponseEntity<ApiResponse<?>> submitScreenshotVerification(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }
            boolean agreed = Boolean.TRUE.equals(body.get("agree"));
            if (!agreed) {
                return ResponseEntity.ok(ApiResponse.error(400,
                        "请先勾选同意《信息使用说明》：所提交资料仅用于本平台学生身份审核"));
            }
            StudentVerification record = studentVerificationService.submit(
                    userOpt.get().getId(),
                    str(body.get("realName")),
                    str(body.get("school")),
                    str(body.get("studentNo")),
                    str(body.get("enrollmentYear")),
                    str(body.get("department")),
                    str(body.get("proofImage")),
                    str(body.get("remark")));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", record.getId());
            result.put("status", record.getStatus());
            result.put("createdAt", record.getCreatedAt());
            return ResponseEntity.ok(ApiResponse.success("已提交，等待管理员审核", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("提交支付宝学生认证截图认证失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "提交失败，请稍后重试"));
        }
    }

    /**
     * 上传认证截图到<b>私有目录</b>，返回不透明的文件名标识。
     *
     * <p>截图不落公开的 {@code /uploads/**} 目录：那里有静态资源映射，
     * 任何拿到 URL 的人都能直接打开。认证截图只有审核人员能看，
     * 因此它经带鉴权的接口按申请记录读取。</p>
     *
     * <p>校验格式/大小/文件头在 {@link UploadService#storePrivateImage} 里，
     * 这里只负责鉴权与错误响应。</p>
     */
    @PostMapping("/student-verification/proof")
    public ResponseEntity<ApiResponse<?>> uploadScreenshotProof(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }
            String proofId = uploadService.storePrivateImage(file);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proofId", proofId);
            return ResponseEntity.ok(ApiResponse.success("上传成功", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("认证截图上传失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "图片上传失败，请稍后重试"));
        }
    }

    /**
     * 查询我的支付宝截图辅助认证进度（最新一条申请）。
     *
     * <p>返回 {@code data = null} 表示从未提交过。拒绝时带 {@code rejectReason}，
     * 前端据此展示原因并允许重新提交。本人查询，姓名与学号不脱敏。</p>
     */
    @GetMapping("/student-verification")
    public ResponseEntity<ApiResponse<?>> myScreenshotVerification(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Optional<User> userOpt = getUserFromToken(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }
            StudentVerification record = studentVerificationService.latestOf(userOpt.get().getId());
            if (record == null) {
                return ResponseEntity.ok(ApiResponse.success("获取成功", null));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", record.getId());
            result.put("method", record.getMethod());
            result.put("realName", record.getRealName());
            result.put("school", record.getSchool());
            result.put("studentNo", record.getStudentNo());
            result.put("enrollmentYear", record.getEnrollmentYear());
            result.put("department", record.getDepartment());
            result.put("proofImage", record.getProofImage());
            result.put("remark", record.getRemark());
            result.put("status", record.getStatus());
            result.put("rejectReason", record.getRejectReason());
            result.put("createdAt", record.getCreatedAt());
            result.put("reviewedAt", record.getReviewedAt());
            result.put("expiresAt", record.getExpiresAt());
            return ResponseEntity.ok(ApiResponse.success("获取成功", result));
        } catch (Exception e) {
            log.error("查询认证进度失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取失败"));
        }
    }

    /** Map 值安全转字符串：null 与不存在键统一返回 null */
    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 卖家的评价统计：好评率、平均星级、评价总数。
     *
     * <p>全部由 SQL 聚合实时计算，不存冗余字段——冗余的统计值需要处理
     * "评价新增/修改/删除时何时同步"的问题，而评价一经提交不可修改也不可删除，
     * 实时聚合的代价（一次索引扫描）远小于同步逻辑出错的风险。</p>
     */
    @GetMapping("/user/{id}/review-stats")
    public ResponseEntity<ApiResponse<?>> getReviewStats(@PathVariable Long id) {
        try {
            Map<String, Object> stats = reviewService.statsOf(id);
            return ResponseEntity.ok(ApiResponse.success("获取成功", stats));
        } catch (Exception e) {
            log.error("获取评价统计失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取失败"));
        }
    }

    /**
     * 卖家收到的文字评价（默认最近 5 条）。
     *
     * <p>与 review-stats 同为公开数据：商品详情页要让买家看到"别人怎么说"，
     * 而不是只有一个好评率数字。limit 由服务层收敛到 1~20。</p>
     */
    @GetMapping("/user/{id}/reviews")
    public ResponseEntity<ApiResponse<?>> getSellerReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") Integer limit) {
        try {
            return ResponseEntity.ok(ApiResponse.success("获取成功",
                    reviewService.listForSeller(id, limit)));
        } catch (Exception e) {
            log.error("获取卖家评价列表失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取失败"));
        }
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<ApiResponse<?>> getUserById(@PathVariable Long id) {
        try {
            User user = userRepository.selectById(id);
            if (user == null) {
                return ResponseEntity.ok(ApiResponse.error(404, "用户不存在"));
            }

            // 公开资料白名单：只返回展示所需的字段。
            // email / phone 属于联系方式，不能因为「按 id 查详情」就无条件下发——
            // 本接口无需登录，按自增 id 遍历即可批量收集全站邮箱与手机号。
            // 本人资料走 /profile，管理端走 /api/admin/users/{id}。
            return ResponseEntity.ok(ApiResponse.success("获取成功", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "avatar", user.getAvatar() != null ? user.getAvatar() : "",
                    "bio", user.getBio() != null ? user.getBio() : "",
                    "location", user.getLocation() != null ? user.getLocation() : "",
                    // 认证徽章需要这个字段；真实姓名属敏感信息，不在公开接口返回
                    "studentVerified", user.isStudentVerifiedUser()
            )));
        } catch (Exception e) {
            log.error("获取用户信息失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取失败"));
        }
    }
}
