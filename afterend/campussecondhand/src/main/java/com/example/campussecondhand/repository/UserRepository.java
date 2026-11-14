package com.example.campussecondhand.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campussecondhand.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserRepository extends BaseMapper<User> {
    /**
     * 按用户名查用户。
     *
     * <p>{@code @Select} 不会走 MyBatis-Plus 的逻辑删除插件，因此必须手写
     * {@code deleted = 0}：否则管理员「删除」过的账号仍然能用旧 token 登录，
     * 封禁与注销都会失效。</p>
     */
    @Select("SELECT * FROM users WHERE username = #{username} AND deleted = 0 LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * 按学号查用户，用于认证时判断该学号是否已被占用。
     *
     * <p>同 {@link #findByUsername}：{@code @Select} 不会经过 MyBatis-Plus 的
     * 逻辑删除插件，必须手写 {@code deleted = 0}。漏掉它会让已注销账号继续
     * 占着学号——新用户认证时被判定为「已被占用」，而占用者其实早已不在。</p>
     */
    @Select("SELECT * FROM users WHERE student_no = #{studentNo} AND deleted = 0 LIMIT 1")
    User selectByStudentNo(@Param("studentNo") String studentNo);

    /**
     * 取出疑似明文口令的用户，供一次性迁移脚本改写为 BCrypt。
     *
     * <p>为什么能这样筛：BCrypt 密文恒为 60 字符且以 {@code $2} 开头，因此
     * 「长度过短」或「不以 {@code $2} 开头」任一成立即可判定为疑似明文。
     * 前缀里不能再拼一个 {@code $}：BCrypt 的形态是 {@code $2a$10$…}，
     * 写成 {@code $2$%} 会因第四位是字母而永远匹配不上，导致本条件失效、
     * 全表用户都被当成候选。</p>
     *
     * <p>用 {@code LENGTH(...)} 而非 {@code CHAR_LENGTH(...)}：两者对 ASCII
     * 口令结果一致，但 {@code LENGTH} 走字节长度，与 BCrypt 固定 60 字节的
     * 判据保持同一量纲。</p>
     */
    @Select("""
        SELECT * FROM users
        WHERE deleted = 0
          AND (password IS NULL
               OR LENGTH(password) < #{maxPlaintextLength}
               OR password NOT LIKE CONCAT(#{bcryptPrefix}, '%'))
        """)
    List<User> findPlaintextPasswordUsers(@Param("maxPlaintextLength") int maxPlaintextLength,
                                          @Param("bcryptPrefix") String bcryptPrefix);
}


