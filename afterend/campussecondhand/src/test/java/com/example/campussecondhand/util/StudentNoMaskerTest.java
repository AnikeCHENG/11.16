package com.example.campussecondhand.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 学号脱敏规则。
 *
 * <p>掩码规则是「前 4 位 + 固定 4 个星号 + 后 2 位」。星号数量刻意不随学号长度变化：
 * 学号长度是 10~12 位，若跟着浮动，个人中心那一行的文本宽度会在不同用户之间抖动。</p>
 */
class StudentNoMaskerTest {

    @Test
    @DisplayName("10 位学号 -> 2023****56")
    void masksTenDigitStudentNo() {
        assertThat(StudentNoMasker.mask("2023123456")).isEqualTo("2023****56");
    }

    @Test
    @DisplayName("12 位学号 -> 2020****78，星号仍是 4 个")
    void masksTwelveDigitStudentNo() {
        assertThat(StudentNoMasker.mask("202012345678")).isEqualTo("2020****78");
    }

    @Test
    @DisplayName("11 位学号同样固定 4 个星号，保证 UI 宽度稳定")
    void keepsMaskWidthConstantAcrossLengths() {
        assertThat(StudentNoMasker.mask("20231234567")).isEqualTo("2023****67");
        // 三种长度的输出等宽
        assertThat(StudentNoMasker.mask("2023123456")).hasSameSizeAs(
                StudentNoMasker.mask("202012345678"));
        assertThat(StudentNoMasker.mask("202012345678")).hasSameSizeAs(
                StudentNoMasker.mask("20231234567"));
    }

    @Test
    @DisplayName("空值返回空串——前端据此隐藏整块认证区，而不是渲染假学号")
    void blankInputYieldsEmptyString() {
        assertThat(StudentNoMasker.mask(null)).isEmpty();
        assertThat(StudentNoMasker.mask("")).isEmpty();
        assertThat(StudentNoMasker.mask("   ")).isEmpty();
    }

    @Test
    @DisplayName("过短的输入整串打码，不保留首尾")
    void tooShortInputIsFullyMasked() {
        // 长度 <= 4 时无法安全保留首 2 尾 2，直接打码
        assertThat(StudentNoMasker.mask("2020")).isEqualTo("****");
        assertThat(StudentNoMasker.mask("123")).isEqualTo("****");
    }

    @Test
    @DisplayName("脱敏结果不得泄露完整学号的中间段")
    void maskDoesNotLeakFullStudentNo() {
        String no = "202012345678";
        String masked = StudentNoMasker.mask(no);

        assertThat(masked).doesNotContain("1234");
        assertThat(masked).doesNotContain(no);
        assertThat(masked).hasSize(10);
        // 只允许保留首 4 尾 2
        assertThat(masked).startsWith("2020").endsWith("78");
    }

    @Test
    @DisplayName("首尾有空白时先裁剪，不把空格算进位数")
    void trimsWhitespaceBeforeMasking() {
        assertThat(StudentNoMasker.mask("  2023123456  ")).isEqualTo("2023****56");
    }

    @Test
    @DisplayName("未认证用户（学号为空）拿到空串，前端不会显示 ****")
    void unverifiedUserGetsEmptyString() {
        // 未认证用户 users.student_no 为 null，这是最常见的输入
        assertThat(StudentNoMasker.mask(null)).isEmpty();
    }
}
