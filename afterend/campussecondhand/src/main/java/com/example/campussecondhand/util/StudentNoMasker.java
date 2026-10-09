package com.example.campussecondhand.util;

/**
 * 学号脱敏。
 *
 * <p><b>为什么需要脱敏：</b>学号在本系统里是身份证一样的准身份标识。它同时是
 * {@code users} 表的唯一索引（{@code uk_student_no}），一旦完整出现在任何接口响应、
 * 浏览器 devtools、截图或日志里，就等于把这名学生的校园身份公开了。因此
 * {@code User.studentNo} 上标了 {@code JsonProperty.Access.WRITE_ONLY}——
 * 能写进来（认证时提交），但不会自动序列化出去。</p>
 *
 * <p>唯一需要把学号显示给人看的地方是本人自己的认证信息区，那里也不需要完整值，
 * {@code 2020****78} 足够确认「填的是不是这个学号」。</p>
 *
 * <p><b>为什么星号数量固定为 4 而不按长度变化：</b>学号长度是 10~12 位
 * （见 {@code StudentVerifyService} 的 {@code ^20\d{2}\d{6,8}$}）。若星号数量跟着
 * 长度浮动，个人中心那一行的文本宽度会在不同用户之间跳动，UI 上表现为元素抖动。
 * 固定 4 个星号让所有已认证用户的这一行等宽。</p>
 */
public final class StudentNoMasker {

    /** 中间固定替换成的星号数量，与学号实际长度无关 */
    private static final String MASK = "****";

    /** 小于这个长度不足以区分不同学号，一律整串打码 */
    private static final int MIN_LENGTH_TO_KEEP_ENDS = 4;

    private StudentNoMasker() {
    }

    /**
     * 把学号脱敏为「前 4 位 + **** + 后 2 位」。
     *
     * <p>输入为空、过短或全空白时返回空字符串——前端据此隐藏整块认证信息区，
     * 而不是渲染一个「****」的假学号。</p>
     *
     * <pre>
     * 2023123456   -> 2023****56
     * 202012345678 -> 2020****78
     * null / ""    -> ""
     * </pre>
     */
    public static String mask(String studentNo) {
        if (studentNo == null) {
            return "";
        }
        String no = studentNo.trim();
        if (no.isEmpty()) {
            return "";
        }
        // 过短的输入无法安全地保留首尾，直接整串打码
        if (no.length() <= MIN_LENGTH_TO_KEEP_ENDS) {
            return MASK;
        }
        String head = no.substring(0, 4);
        String tail = no.substring(no.length() - 2);
        return head + MASK + tail;
    }
}
