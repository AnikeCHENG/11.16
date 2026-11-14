package com.example.campussecondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 学生认证申请（支付宝学生认证截图辅助认证）。
 *
 * <p>users 表的 is_student_verified / student_no / real_name 是"认证结果"，
 * 本表是"申请过程"的留痕：申请 → 待审核 → 通过/拒绝 → （到期）过期。
 * 原有学号即交即过认证不经过本表，互不影响。</p>
 *
 * <p>与 {@link Report} 一致：没有逻辑删除列，created_at 由
 * {@code DEFAULT CURRENT_TIMESTAMP} 兜底，服务层写入时显式赋值保证确定性。</p>
 */
@TableName("student_verification")
public class StudentVerification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人ID */
    @TableField("user_id")
    private Long userId;

    /** 认证方式，当前仅 alipay_student_screenshot */
    @TableField("method")
    private String method;

    /** 真实姓名（列表页展示时脱敏） */
    @TableField("real_name")
    private String realName;

    /** 学校名称 */
    @TableField("school")
    private String school;

    /** 学号，选填（列表页展示时脱敏） */
    @TableField("student_no")
    private String studentNo;

    /** 入学年份，如 2023 */
    @TableField("enrollment_year")
    private Integer enrollmentYear;

    /** 学院或专业，选填 */
    @TableField("department")
    private String department;

    /** 支付宝学生认证结果截图路径，形如 /uploads/2026/11/xxx.jpg */
    @TableField("proof_image")
    private String proofImage;

    /** 备注说明，选填 */
    @TableField("remark")
    private String remark;

    /** pending / approved / rejected / expired */
    @TableField("status")
    private String status;

    /** 审核拒绝理由，用户端可见 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 审核管理员ID */
    @TableField("reviewed_by")
    private Long reviewedBy;

    /** 提交时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 审核时间 */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    /** 认证有效期截止时间，审核通过时写入 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }

    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }

    public Integer getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(Integer enrollmentYear) { this.enrollmentYear = enrollmentYear; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getProofImage() { return proofImage; }
    public void setProofImage(String proofImage) { this.proofImage = proofImage; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
