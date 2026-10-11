package com.example.campussecondhand.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final String from;
    private final SecureRandom random = new SecureRandom();

    public EmailService(StringRedisTemplate redisTemplate, JavaMailSender mailSender,
                        @Value("${spring.mail.username}") String from) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * 触发验证码发送频控时抛出（60 秒一次 / 每日 10 封）。
     *
     * <p>单独定义类型，是为了让调用方能把它与"邮件服务器挂了"区分开：
     * 前者是 429（客户端稍后再试），后者是 500（服务端故障）。</p>
     */
    public static class SendTooFrequentException extends RuntimeException {
        public SendTooFrequentException(String message) {
            super(message);
        }
    }

    /**
     * 占用一次发送配额：60 秒内只能发一次，每日最多 10 封。
     *
     * <p><b>忘记密码这类"按邮箱触发"的接口，无论邮箱是否属于某个用户，
     * 都必须先调用本方法。</b>否则"第二次请求返回 429"只对已注册邮箱生效，
     * 频控本身就成了账号枚举的探针——与直接回 {@code 404 邮箱不存在} 一样糟。</p>
     *
     * @throws SendTooFrequentException 触发频控或每日上限
     */
    public void reserveSendSlot(String email) {
        String lastKey = "email:code:last:" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lastKey))) {
            throw new SendTooFrequentException("60秒内只能发送一次");
        }

        String dailyKey = "email:code:daily:" + email + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = redisTemplate.opsForValue().increment(dailyKey);
        if (count != null && count == 1) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        if (count != null && count > 10) {
            throw new SendTooFrequentException("今日发送次数已达上限");
        }

        redisTemplate.opsForValue().set(lastKey, "1", Duration.ofSeconds(60));
    }

    /** 注册验证码：占配额 → 生成并暂存 → 发送邮件 */
    public void sendCode(String email) {
        reserveSendSlot(email);
        String codeStr = storeCode(email);
        send(email, "校园二手注册验证码", codeStr);
    }

    /**
     * 密码重置验证码：与注册验证码共用同一套频控、有效期与尝试次数限制，
     * 仅邮件文案与主题不同——用户能一眼看出这封邮件是干什么的。
     */
    public void sendResetCode(String email) {
        reserveSendSlot(email);
        String codeStr = storeCode(email);
        send(email, "校园二手密码重置验证码", codeStr);
    }

    private String storeCode(String email) {
        int code = 100000 + random.nextInt(900000);
        String codeStr = String.valueOf(code);
        redisTemplate.opsForValue().set("email:code:" + email, codeStr, Duration.ofMinutes(5));
        // 新验证码作废旧验证码的错误次数计数，避免上一轮的错误次数累加到新验证码上
        redisTemplate.delete("email:code:attempts:" + email);
        return codeStr;
    }

    private void send(String email, String subject, String codeStr) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText("你的验证码是：" + codeStr + "，5分钟内有效。");
        mailSender.send(message);
    }

    public void verify(String email, String code) {
        String stored = redisTemplate.opsForValue().get("email:code:" + email);
        if (stored == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }

        String attemptsKey = "email:code:attempts:" + email;
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr == null ? 0 : Integer.parseInt(attemptsStr);

        if (!stored.equals(code)) {
            attempts++;
            if (attempts > 5) {
                redisTemplate.delete("email:code:" + email);
                redisTemplate.delete(attemptsKey);
            } else {
                redisTemplate.opsForValue().set(attemptsKey, String.valueOf(attempts), Duration.ofMinutes(5));
            }
            throw new RuntimeException("验证码错误");
        }

        redisTemplate.delete("email:code:" + email);
        redisTemplate.delete(attemptsKey);
    }
}
