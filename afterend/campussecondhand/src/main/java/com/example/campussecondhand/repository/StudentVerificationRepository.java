package com.example.campussecondhand.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campussecondhand.entity.StudentVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学生认证申请（支付宝学生认证截图辅助认证）Mapper。
 *
 * <p>本表没有逻辑删除列，原生 SQL 不需要手写 {@code WHERE deleted = 0}。</p>
 */
@Mapper
public interface StudentVerificationRepository extends BaseMapper<StudentVerification> {

    /**
     * 统计申请人当前处于 pending 状态的申请数。
     *
     * <p>用于「同一用户同时只能有一条待审核申请」的防重复提交；
     * 命中 {@code idx_user_created} 前缀索引。</p>
     */
    @Select("""
        SELECT COUNT(*) FROM student_verification
        WHERE user_id = #{userId} AND status = 'pending'
        """)
    long countPendingByUser(@Param("userId") Long userId);
}
