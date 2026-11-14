package com.example.campussecondhand.service;

import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.entity.StudentVerification;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.StudentVerificationStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.StudentVerificationRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付宝学生认证截图辅助认证服务测试。
 *
 * <p>重点验证三条安全/合规约束：</p>
 * <ol>
 *   <li>已通过既有认证的用户不能再提交、审核通过时不会触碰 users（不降级）；</li>
 *   <li>学号被他人占用时既不能提交、也不能通过审核；</li>
 *   <li>拒绝必须留理由，通过必须写 reviewed_at 与有效期。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class StudentVerificationServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ADMIN_ID = 1L;

    @Mock
    private StudentVerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UploadService uploadService;
    @InjectMocks
    private StudentVerificationService service;

    private User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername("tester");
        u.setIsStudentVerified(0);
        return u;
    }

    private StudentVerification pending() {
        StudentVerification r = new StudentVerification();
        r.setId(100L);
        r.setUserId(USER_ID);
        r.setRealName("张三");
        r.setSchool("某某大学");
        r.setEnrollmentYear(2023);
        r.setProofImage("0123456789abcdef0123456789abcdef.png");
        r.setStatus(StudentVerificationStatus.PENDING.getCode());
        return r;
    }

    @Test
    @DisplayName("提交成功：进入 pending，method 固定为支付宝截图辅助认证")
    void submitSuccess() {
        when(userRepository.selectById(USER_ID)).thenReturn(user());
        when(verificationRepository.countPendingByUser(USER_ID)).thenReturn(0L);

        StudentVerification record = service.submit(USER_ID, "张三", "某某大学", null,
                "2023", "计算机学院", "0123456789abcdef0123456789abcdef.png", null);

        assertThat(record.getStatus()).isEqualTo("pending");
        assertThat(record.getMethod()).isEqualTo(StudentVerificationService.METHOD_ALIPAY_SCREENSHOT);
        assertThat(record.getStudentNo()).isNull();
        verify(verificationRepository).insert(record);
    }

    @Test
    @DisplayName("已通过既有认证的用户不能提交辅助认证")
    void submitRejectedWhenAlreadyVerified() {
        User u = user();
        u.setIsStudentVerified(1);
        when(userRepository.selectById(USER_ID)).thenReturn(u);

        assertThatThrownBy(() -> service.submit(USER_ID, "张三", "某某大学", null,
                "2023", null, "0123456789abcdef0123456789abcdef.png", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已完成学生认证");

        verify(verificationRepository, never()).insert(any());
    }

    @Test
    @DisplayName("已有待审核申请时不能重复提交")
    void submitRejectedWhenPendingExists() {
        when(userRepository.selectById(USER_ID)).thenReturn(user());
        when(verificationRepository.countPendingByUser(USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(USER_ID, "张三", "某某大学", null,
                "2023", null, "0123456789abcdef0123456789abcdef.png", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("待审核");
    }

    @Test
    @DisplayName("学号被其他账号占用时不能提交")
    void submitRejectedWhenStudentNoOccupied() {
        User other = new User();
        other.setId(99L);
        when(userRepository.selectByStudentNo("202012345678")).thenReturn(other);

        assertThatThrownBy(() -> service.submit(USER_ID, "张三", "某某大学", "202012345678",
                "2023", null, "0123456789abcdef0123456789abcdef.png", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该学号已被其他账号认证");

        // 非法输入（学号占用）不该再往后走
        verify(userRepository, never()).selectById(any());
    }

    @Test
    @DisplayName("截图必须是本站上传的 jpg/png 路径")
    void submitRejectedWhenProofImageInvalid() {
        assertThatThrownBy(() -> service.submit(USER_ID, "张三", "某某大学", null,
                "2023", null, "http://evil.example.com/shot.png", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("截图无效");

        assertThatThrownBy(() -> service.submit(USER_ID, "张三", "某某大学", null,
                "2023", null, "0123456789abcdef0123456789abcdef.gif", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JPG / PNG");

        verify(verificationRepository, never()).insert(any());
    }

    @Test
    @DisplayName("审核通过：写入用户认证字段、置 is_student_verified、写有效期")
    void approveMarksUserVerified() {
        StudentVerification record = pending();
        User u = user();
        when(verificationRepository.selectById(100L)).thenReturn(record);
        when(userRepository.selectById(USER_ID)).thenReturn(u);

        StudentVerification reviewed = service.review(100L, "approve", "", ADMIN_ID, "admin");

        assertThat(reviewed.getStatus()).isEqualTo("approved");
        assertThat(reviewed.getReviewedAt()).isNotNull();
        assertThat(reviewed.getReviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(reviewed.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(u.getIsStudentVerified()).isEqualTo(1);
        assertThat(u.getRealName()).isEqualTo("张三");
        verify(userRepository).updateById(u);
        verify(verificationRepository).updateById(record);
    }

    @Test
    @DisplayName("用户已有既有认证时，通过审核不触碰 users（不降级/不覆盖）")
    void approveKeepsExistingVerification() {
        StudentVerification record = pending();
        User u = user();
        u.setIsStudentVerified(1);
        u.setStudentNo("202012345678");
        u.setRealName("李四");
        when(verificationRepository.selectById(100L)).thenReturn(record);
        when(userRepository.selectById(USER_ID)).thenReturn(u);

        service.review(100L, "approve", "", ADMIN_ID, "admin");

        // users 保持原状：学号、姓名、认证标志都不被截图申请覆盖
        assertThat(u.getStudentNo()).isEqualTo("202012345678");
        assertThat(u.getRealName()).isEqualTo("李四");
        assertThat(u.getIsStudentVerified()).isEqualTo(1);
        verify(userRepository, never()).updateById(any());
        verify(verificationRepository).updateById(record);
    }

    @Test
    @DisplayName("审核通过撞上学号占用（并发窗口）时抛业务异常，不写用户")
    void approveRejectedWhenStudentNoOccupiedConcurrently() {
        StudentVerification record = pending();
        record.setStudentNo("202012345678");
        User u = user();
        User other = new User();
        other.setId(99L);
        when(verificationRepository.selectById(100L)).thenReturn(record);
        when(userRepository.selectById(USER_ID)).thenReturn(u);
        when(userRepository.selectByStudentNo("202012345678")).thenReturn(other);

        assertThatThrownBy(() -> service.review(100L, "approve", "", ADMIN_ID, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该学号已被其他账号认证");

        verify(userRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("审核通过时学号唯一索引兜底：DuplicateKeyException 转业务异常")
    void approveTranslatesDuplicateKey() {
        StudentVerification record = pending();
        record.setStudentNo("202012345678");
        User u = user();
        when(verificationRepository.selectById(100L)).thenReturn(record);
        when(userRepository.selectById(USER_ID)).thenReturn(u);
        when(userRepository.selectByStudentNo("202012345678")).thenReturn(null);
        when(userRepository.updateById(any(User.class)))
                .thenThrow(new DuplicateKeyException("uk_student_no"));

        assertThatThrownBy(() -> service.review(100L, "approve", "", ADMIN_ID, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该学号已被其他账号认证");
    }

    @Test
    @DisplayName("拒绝必须填写理由，理由展示给申请人")
    void rejectRequiresReason() {
        when(verificationRepository.selectById(100L)).thenReturn(pending());

        assertThatThrownBy(() -> service.review(100L, "reject", "  ", ADMIN_ID, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("拒绝理由");

        StudentVerification reviewed = service.review(100L, "reject", "截图模糊", ADMIN_ID, "admin");
        assertThat(reviewed.getStatus()).isEqualTo("rejected");
        assertThat(reviewed.getRejectReason()).isEqualTo("截图模糊");
        assertThat(reviewed.getReviewedAt()).isNotNull();
        // 拒绝不写有效期
        assertThat(reviewed.getExpiresAt()).isNull();
        verify(userRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("非 pending 状态不能重复审核")
    void reviewGuardedAgainstDoubleReview() {
        StudentVerification record = pending();
        record.setStatus(StudentVerificationStatus.APPROVED.getCode());
        when(verificationRepository.selectById(100L)).thenReturn(record);

        assertThatThrownBy(() -> service.review(100L, "approve", "", ADMIN_ID, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    @DisplayName("审核动作白名单")
    void reviewRejectsUnknownAction() {
        when(verificationRepository.selectById(100L)).thenReturn(pending());

        assertThatThrownBy(() -> service.review(100L, "delete", "", ADMIN_ID, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("审核动作不合法");
    }

    @Test
    @DisplayName("管理端列表脱敏：姓名只留姓氏、学号打码；详情不脱敏")
    void adminViewsAreMasked() {
        StudentVerification record = pending();
        record.setStudentNo("202012345678");
        User u = user();
        when(verificationRepository.selectById(100L)).thenReturn(record);
        when(userRepository.selectById(USER_ID)).thenReturn(u);

        Map<String, Object> detail = service.detailForAdmin(100L);
        assertThat(detail.get("realName")).isEqualTo("张三");
        assertThat(detail.get("studentNo")).isEqualTo("202012345678");

        // 列表视图走 masked=true 分支，直接断言静态脱敏方法与 StudentNoMasker 对齐
        assertThat(StudentVerificationService.maskName("张三")).isEqualTo("张*");
        assertThat(StudentVerificationService.maskName("欧阳")).isEqualTo("欧*");
        assertThat(StudentVerificationService.maskName("王")).isEqualTo("王");
        assertThat(StudentVerificationService.maskName(" ")).isEmpty();
    }

    @Test
    @DisplayName("私有截图只经管理端接口取回，读不到就报「图片不存在」")
    void proofIsServedThroughAdminOnlyPath() {
        StudentVerification record = pending();
        when(verificationRepository.selectById(100L)).thenReturn(record);
        when(uploadService.readPrivateImage(record.getProofImage()))
                .thenReturn(new byte[]{1, 2, 3});

        StudentVerificationService.ProofFile proof = service.proof(100L);

        assertThat(proof.fileName()).isEqualTo(record.getProofImage());
        assertThat(proof.bytes()).containsExactly(1, 2, 3);
        verify(uploadService).readPrivateImage(record.getProofImage());
    }

    @Test
    @DisplayName("过期懒翻：approved 且 expires_at 已过的申请查询时翻成 expired")
    void latestFlipsExpiredStatus() {
        StudentVerification record = pending();
        record.setStatus(StudentVerificationStatus.APPROVED.getCode());
        record.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(verificationRepository.selectOne(any())).thenReturn(record);

        StudentVerification latest = service.latestOf(USER_ID);

        assertThat(latest.getStatus()).isEqualTo("expired");
        ArgumentCaptor<StudentVerification> captor = ArgumentCaptor.forClass(StudentVerification.class);
        verify(verificationRepository).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("expired");
    }
}
