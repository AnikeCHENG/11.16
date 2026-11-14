package com.example.campussecondhand.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.common.PageResult;
import com.example.campussecondhand.entity.StudentVerification;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.StudentVerificationStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.StudentVerificationRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.util.StudentNoMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生认证申请服务：支付宝学生认证截图辅助认证。
 *
 * <p><b>定位：仅是学校邮箱/学号认证的辅助方式</b>，给无法完成既有认证的学生
 * 一条人工审核通道。三条核心约束：</p>
 * <ol>
 *   <li>用户已完成既有学生认证（users.is_student_verified = 1）时不允许提交，
 *       审核通过时也绝不回退/覆盖已有认证等级；</li>
 *   <li>同一用户同时只能有一条待审核申请，拒绝/过期后可重新提交；</li>
 *   <li>审核通过与拒绝必须同事务留痕（reviewed_at / reject_reason / reviewed_by）。</li>
 * </ol>
 *
 * <p>本服务不采集支付宝账号、密码、支付密码、短信验证码、完整身份证号，
 * 也不提供任何仿冒支付宝的页面——用户在支付宝 App 内自行完成官方"学生认证"，
 * 平台只收集其提交的认证结果截图与基本学籍信息用于人工审核。</p>
 */
@Service
public class StudentVerificationService {

    private static final Logger log = LoggerFactory.getLogger(StudentVerificationService.class);

    /** 认证方式标识：支付宝学生认证截图辅助认证 */
    public static final String METHOD_ALIPAY_SCREENSHOT = "alipay_student_screenshot";

    /** 审核动作 */
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_SCHOOL_LENGTH = 100;
    private static final int MAX_STUDENT_NO_LENGTH = 20;
    private static final int MAX_DEPARTMENT_LENGTH = 100;
    private static final int MAX_REMARK_LENGTH = 255;
    private static final int MAX_REJECT_REASON_LENGTH = 500;

    /** 入学年份：4 位年份，且不晚于当前年份（不支持"预入学"） */
    private static final Pattern ENROLLMENT_YEAR = Pattern.compile("^20\\d{2}$");

    /**
     * 截图标识校验：必须是私有目录里服务端生成的文件名，且仅 jpg / png。
     *
     * <p>上传环节（{@link UploadService#storePrivateImage}）已做魔术字节嗅探与 UUID 重命名，
     * 截图落在<b>不注册静态资源映射</b>的私有目录里——外部没有任何 URL 能直接取到它，
     * 只能经带鉴权的管理端接口按申请记录读取。这里再校验一次标识形态，
     * 防止直接调提交接口把任意外链/路径塞进库。</p>
     */
    private static final Pattern PROOF_IMAGE_PATH =
            Pattern.compile("^[0-9a-f]{32}\\.(jpg|png)$");

    /** 审核通过后的认证有效期（天）。到期后用户端展示为「已过期」，可重新提交 */
    private static final int VALIDITY_DAYS = 365;

    private final StudentVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final UploadService uploadService;

    @Autowired
    public StudentVerificationService(StudentVerificationRepository verificationRepository,
                                      UserRepository userRepository,
                                      UploadService uploadService) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.uploadService = uploadService;
    }

    /**
     * 读取某条申请的认证截图（管理端审核用）。
     *
     * <p>截图存在私有目录里，没有对外 URL，只能经此接口按申请记录取。
     * 该接口位于 /api/admin/** 下，由 {@code AdminAuthInterceptor} 校验管理员身份。</p>
     */
    public ProofFile proof(Long id) {
        StudentVerification record = verificationRepository.selectById(id);
        if (record == null) {
            throw new BadRequestException("认证申请不存在");
        }
        String fileName = record.getProofImage();
        return new ProofFile(fileName, uploadService.readPrivateImage(fileName));
    }

    /** 认证截图（文件名 + 字节）；文件名用于判断 Content-Type */
    public record ProofFile(String fileName, byte[] bytes) {
    }

    // ==================== C 端提交 ====================

    /**
     * 提交支付宝学生认证截图认证申请，进入待审核状态。
     *
     * @param userId         当前登录用户
     * @param realName       真实姓名，必填
     * @param school         学校名称，必填
     * @param studentNo      学号，选填
     * @param enrollmentYear 入学年份，必填（4 位年份）
     * @param department     学院或专业，选填
     * @param proofImage     支付宝学生认证结果截图路径，必填（先经 /api/upload/image 上传）
     * @param remark         备注说明，选填
     */
    @Transactional
    public StudentVerification submit(Long userId, String realName, String school, String studentNo,
                                      String enrollmentYear, String department,
                                      String proofImage, String remark) {
        String name = clean(realName);
        if (name.isEmpty()) {
            throw new BadRequestException("请填写真实姓名");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("真实姓名不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }

        String schoolName = clean(school);
        if (schoolName.isEmpty()) {
            throw new BadRequestException("请填写学校名称");
        }
        if (schoolName.length() > MAX_SCHOOL_LENGTH) {
            throw new BadRequestException("学校名称不能超过 " + MAX_SCHOOL_LENGTH + " 个字符");
        }

        // 学号选填：不同学校编号规则不一，不做 ^20\d{2}\d{6,8}$ 的强校验，
        // 只做长度与占用检查（与既有认证共用 uk_student_no 唯一索引）
        String no = clean(studentNo);
        if (no.length() > MAX_STUDENT_NO_LENGTH) {
            throw new BadRequestException("学号不能超过 " + MAX_STUDENT_NO_LENGTH + " 个字符");
        }
        if (!no.isEmpty()) {
            User occupied = userRepository.selectByStudentNo(no);
            if (occupied != null && !occupied.getId().equals(userId)) {
                throw new BadRequestException("该学号已被其他账号认证，请勿重复填写");
            }
        }

        Integer year = parseEnrollmentYear(enrollmentYear);

        String dept = clean(department);
        if (dept.length() > MAX_DEPARTMENT_LENGTH) {
            throw new BadRequestException("学院/专业不能超过 " + MAX_DEPARTMENT_LENGTH + " 个字符");
        }

        String image = clean(proofImage);
        if (image.isEmpty()) {
            throw new BadRequestException("请上传支付宝学生认证结果截图");
        }
        if (!PROOF_IMAGE_PATH.matcher(image).matches()) {
            throw new BadRequestException("截图无效，请重新上传（仅支持 JPG / PNG）");
        }

        String note = clean(remark);
        if (note.length() > MAX_REMARK_LENGTH) {
            throw new BadRequestException("备注说明不能超过 " + MAX_REMARK_LENGTH + " 个字符");
        }

        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        // 已通过既有认证（学号/学校邮箱）的用户无需也不允许再走辅助认证：
        // 辅助方式只面向"无法完成既有认证"的学生
        if (user.isStudentVerifiedUser()) {
            throw new BadRequestException("你已完成学生认证，无需重复提交");
        }
        if (verificationRepository.countPendingByUser(userId) > 0) {
            throw new BadRequestException("已有待审核的认证申请，请耐心等待审核结果");
        }

        StudentVerification record = new StudentVerification();
        record.setUserId(userId);
        record.setMethod(METHOD_ALIPAY_SCREENSHOT);
        record.setRealName(name);
        record.setSchool(schoolName);
        record.setStudentNo(no.isEmpty() ? null : no);
        record.setEnrollmentYear(year);
        record.setDepartment(dept.isEmpty() ? null : dept);
        record.setProofImage(image);
        record.setRemark(note.isEmpty() ? null : note);
        record.setStatus(StudentVerificationStatus.PENDING.getCode());
        record.setCreatedAt(LocalDateTime.now());
        verificationRepository.insert(record);

        log.info("用户[{}] 提交支付宝学生认证截图辅助认证申请[{}]", user.getUsername(), record.getId());
        return record;
    }

    /** 校验入学年份：4 位、不晚于当前年份 */
    private Integer parseEnrollmentYear(String enrollmentYear) {
        String year = clean(enrollmentYear);
        if (year.isEmpty()) {
            throw new BadRequestException("请填写入学年份");
        }
        if (!ENROLLMENT_YEAR.matcher(year).matches()) {
            throw new BadRequestException("入学年份格式不正确，应为 4 位年份（如 2023）");
        }
        int value = Integer.parseInt(year);
        if (value > YearMonth.now().getYear()) {
            throw new BadRequestException("入学年份不能晚于当前年份");
        }
        return value;
    }

    /**
     * 查询用户最新一条申请（用户端展示认证进度用）。
     *
     * <p>已通过但超过有效期的申请在这里懒翻成 expired：不在定时任务里扫，
     * 因为过期态只影响展示与"允许重新提交"这一件事，谁先查到谁翻页即可。
     * 注意过期<b>不会</b>撤销 users 上已有的认证徽章——徽章的回收是管理端
     * 用户管理的事，与某条辅助认证申请到期无关。</p>
     */
    public StudentVerification latestOf(Long userId) {
        QueryWrapper<StudentVerification> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .orderByDesc("created_at", "id")
                .last("LIMIT 1");
        StudentVerification latest = verificationRepository.selectOne(wrapper);
        if (latest == null) {
            return null;
        }
        if (StudentVerificationStatus.APPROVED.getCode().equals(latest.getStatus())
                && latest.getExpiresAt() != null
                && latest.getExpiresAt().isBefore(LocalDateTime.now())) {
            latest.setStatus(StudentVerificationStatus.EXPIRED.getCode());
            StudentVerification update = new StudentVerification();
            update.setId(latest.getId());
            update.setStatus(StudentVerificationStatus.EXPIRED.getCode());
            verificationRepository.updateById(update);
        }
        return latest;
    }

    // ==================== 管理端查询 ====================

    /**
     * 管理端分页查询申请列表。支持按状态筛选与关键词搜索（命中学校名）。
     *
     * <p>列表页按合规要求脱敏：真实姓名只留姓氏，学号用 {@link StudentNoMasker}。
     * 完整信息走 {@link #detailForAdmin}（仅管理员可见，用于和截图比对）。</p>
     */
    public PageResult<Map<String, Object>> pageForAdmin(PageParam paging, String status, String search) {
        QueryWrapper<StudentVerification> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            if (!StudentVerificationStatus.isValid(status)) {
                throw new BadRequestException("非法的认证状态值");
            }
            wrapper.eq("status", status);
        }
        if (search != null && !search.isBlank()) {
            wrapper.like("school", search.trim());
        }
        // 待审核排最前、其余按提交时间倒序，与"先处理未处理"的处理习惯一致
        wrapper.orderByAsc("status").orderByDesc("created_at", "id");

        IPage<StudentVerification> paged = verificationRepository.selectPage(paging.toPage(), wrapper);
        return PageResult.of(toViewList(paged.getRecords()), paged.getTotal(), paging);
    }

    private List<Map<String, Object>> toViewList(List<StudentVerification> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = loadUsers(
                records.stream().map(StudentVerification::getUserId).toList());

        List<Map<String, Object>> list = new ArrayList<>(records.size());
        for (StudentVerification record : records) {
            list.add(toView(record, users.get(record.getUserId()), true));
        }
        return list;
    }

    /**
     * 管理端申请详情：完整姓名与学号。仅 /api/admin/** 可达，
     * 管理员比对截图与申请人信息时需要未脱敏的值。
     */
    public Map<String, Object> detailForAdmin(Long id) {
        StudentVerification record = verificationRepository.selectById(id);
        if (record == null) {
            throw new BadRequestException("认证申请不存在");
        }
        User user = userRepository.selectById(record.getUserId());
        return toView(record, user, false);
    }

    private Map<String, Object> toView(StudentVerification record, User user, boolean masked) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("userId", record.getUserId());
        view.put("username", user != null ? user.getUsername() : null);
        view.put("method", record.getMethod());
        view.put("realName", masked ? maskName(record.getRealName()) : record.getRealName());
        view.put("school", record.getSchool());
        view.put("studentNo", masked
                ? StudentNoMasker.mask(record.getStudentNo())
                : record.getStudentNo());
        view.put("enrollmentYear", record.getEnrollmentYear());
        view.put("department", record.getDepartment());
        view.put("proofImage", record.getProofImage());
        view.put("remark", record.getRemark());
        view.put("status", record.getStatus());
        view.put("rejectReason", record.getRejectReason());
        view.put("createdAt", record.getCreatedAt());
        view.put("reviewedAt", record.getReviewedAt());
        view.put("expiresAt", record.getExpiresAt());
        return view;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    // ==================== 管理端审核 ====================

    /**
     * 审核申请：通过或拒绝，全程单事务。
     *
     * <p>通过时的标记规则：</p>
     * <ul>
     *   <li>用户已有既有学生认证（学号/学校邮箱）：不触碰 users 任何字段，
     *       原有认证等级不会被降级或替换，仅把本条申请标记为 approved；</li>
     *   <li>用户尚无认证：写入 users.real_name / student_no（学号申请里填了才有）
     *       并置 is_student_verified = 1，与既有认证产物完全同构，
     *       免手续费与全站徽章因此自动生效；</li>
     *   <li>同时写 expires_at（一年有效期），到期后用户端显示已过期、可重新提交。</li>
     * </ul>
     *
     * @param id        申请ID
     * @param action    approve / reject
     * @param reason    拒绝理由（拒绝时必填，用户端可见）；通过时可为空
     * @param adminId   审核管理员ID
     * @param adminName 审核管理员昵称，仅用于日志
     */
    @Transactional
    public StudentVerification review(Long id, String action, String reason,
                                      Long adminId, String adminName) {
        StudentVerification record = verificationRepository.selectById(id);
        if (record == null) {
            throw new BadRequestException("认证申请不存在");
        }
        // 状态守卫：只能审核待处理的申请，防止重复操作
        if (!StudentVerificationStatus.PENDING.getCode().equals(record.getStatus())) {
            throw new BadRequestException("该申请已处理，请勿重复操作");
        }

        String cleanReason = clean(reason);
        boolean approve;
        if (ACTION_APPROVE.equals(action)) {
            approve = true;
        } else if (ACTION_REJECT.equals(action)) {
            approve = false;
            if (cleanReason.isEmpty()) {
                throw new BadRequestException("请填写拒绝理由");
            }
        } else {
            throw new BadRequestException("审核动作不合法");
        }
        if (cleanReason.length() > MAX_REJECT_REASON_LENGTH) {
            throw new BadRequestException("拒绝理由不能超过 " + MAX_REJECT_REASON_LENGTH + " 个字符");
        }

        record.setReviewedAt(LocalDateTime.now());
        record.setReviewedBy(adminId);
        if (approve) {
            applyApproval(record);
            record.setStatus(StudentVerificationStatus.APPROVED.getCode());
            record.setRejectReason(null);
            log.warn("管理员[{}] 通过了学生认证截图申请[{}]（用户 {}）",
                    adminName, record.getId(), record.getUserId());
        } else {
            record.setStatus(StudentVerificationStatus.REJECTED.getCode());
            record.setRejectReason(cleanReason);
            log.warn("管理员[{}] 驳回了学生认证截图申请[{}]，理由:{}", adminName, record.getId(), cleanReason);
        }
        verificationRepository.updateById(record);
        return record;
    }

    private void applyApproval(StudentVerification record) {
        User user = userRepository.selectById(record.getUserId());
        if (user == null) {
            throw new BadRequestException("申请人不存在，无法通过审核");
        }
        // 已有既有认证：保留原状，仅归档本条申请为通过。
        // 这保证"已通过学校邮箱认证的用户不会被截图认证降级或替换"
        if (user.isStudentVerifiedUser()) {
            return;
        }
        // 学号唯一索引是冒用防线：提交时查过一次，审核通过时再防一次并发窗口
        if (record.getStudentNo() != null && !record.getStudentNo().isBlank()) {
            User occupied = userRepository.selectByStudentNo(record.getStudentNo());
            if (occupied != null && !occupied.getId().equals(user.getId())) {
                throw new BadRequestException("该学号已被其他账号认证，无法通过；请驳回并联系申请人核实");
            }
            user.setStudentNo(record.getStudentNo());
        }
        user.setRealName(record.getRealName());
        user.setIsStudentVerified(1);
        try {
            userRepository.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("该学号已被其他账号认证，无法通过；请驳回并联系申请人核实");
        }
        record.setExpiresAt(LocalDateTime.now().plusDays(VALIDITY_DAYS));
    }

    /** 真实姓名脱敏：保留姓氏，其余替换为星号 */
    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String value = name.trim();
        if (value.length() == 1) {
            return value;
        }
        return value.charAt(0) + "*".repeat(value.length() - 1);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
