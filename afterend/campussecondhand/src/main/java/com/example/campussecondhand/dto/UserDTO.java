// 修改后的UserDTO类
package com.example.campussecondhand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;

    // 邮箱只做基础格式校验：必须含 @，且 @ 之后还要有一个点。
    // 不做域名/后缀白名单——gmail、qq、企业域名、任意后缀一视同仁。
    //
    // 这里不用 @Email：jakarta 的 @Email 允许 missing@tld 这种「@ 后没有点」的
    // 形式，而前端 Register.vue 用的是要求带点的正则，两边判定会打架。
    // 换成显式 @Pattern 后，前后端口径一致，且少一个注解依赖。
    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于6位")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 6, message = "确认密码长度不能少于6位")
    private String confirmPassword;

    private String code;

    // getter and setter
    //
    // username / email / code 三个 setter 必须 trim，而且只能放在 setter 里：
    // Jackson 反序列化（调用 setter）发生在 @Valid 校验之前，所以在这儿 trim，
    // 上面的 @Pattern / @NotBlank 校验到的就是去掉首尾空格后的值。
    // 若改到 Controller 方法体里 trim，校验早就判失败返回 400 了，根本到不了方法体。
    //
    // 起因：手机输入法/自动补全常在邮箱末尾留下一个空格，肉眼不可见，
    // 却让 ^[^\s@]+@[^\s@]+\.[^\s@]+$ 判失败，用户只看到「邮箱格式不正确」。
    //
    // 注意：password / confirmPassword 刻意不 trim——密码里的首尾空格是合法字符。
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? null : username.trim(); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code == null ? null : code.trim(); }
}
