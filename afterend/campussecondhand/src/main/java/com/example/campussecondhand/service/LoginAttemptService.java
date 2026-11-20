package com.example.campussecondhand.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 登录失败频控（防暴力破解 / 撞库）。
 *
 * <p><b>此前：</b>{@code POST /api/auth/login} 对同一账号连续输错密码没有任何限制，
 * 实测 12 次错误密码全部返回 401、无锁定，6 位纯数字密码可被直接穷举。</p>
 *
 * <p><b>规则：</b></p>
 * <ul>
 *   <li>按<b>用户名</b>计数：窗口内失败达到 {@code maxFailures}（默认 5）次即锁定
 *       {@code lockMinutes}（默认 15）分钟，锁定期间即使密码正确也拒绝；</li>
 *   <li>按<b>客户端 IP</b> 计数（{@code app.login.max-ip-failures}，<b>默认 0 = 关闭</b>）：
 *       用来挡「一个密码试遍所有账号」的喷洒攻击。默认关闭是因为本项目只信任直连地址，
 *       经 Vite 代理 / Nginx 转发时所有用户共用同一个来源 IP，开启后 N 次失败会把所有人锁在门外。
 *       直连部署或配好可信代理后再按需打开；</li>
 *   <li>不存在的用户名同样计数——否则「锁定 / 不锁定」就成了账号枚举信号；</li>
 *   <li>登录成功清零该用户名的计数（IP 计数不清，避免攻击者用自己的账号刷掉 IP 计数）。</li>
 * </ul>
 *
 * <p><b>Redis 不可用时放行（fail-open）并打 WARN：</b>频控是加固项，
 * 不能因为缓存挂了就让所有人都登不上。</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final String USER_FAIL = "login:fail:user:";
    static final String USER_LOCK = "login:lock:user:";
    static final String IP_FAIL = "login:fail:ip:";
    static final String IP_LOCK = "login:lock:ip:";

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final int maxIpFailures;
    private final Duration window;
    private final Duration lock;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.max-ip-failures:0}") int maxIpFailures,
                               @Value("${app.login.window-minutes:15}") long windowMinutes,
                               @Value("${app.login.lock-minutes:15}") long lockMinutes) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.maxIpFailures = maxIpFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lock = Duration.ofMinutes(lockMinutes);
    }

    /**
     * 当前是否被锁定。
     *
     * @return 剩余锁定秒数；未锁定返回 0
     */
    public long lockedSeconds(String username, String ip) {
        try {
            long userTtl = ttl(USER_LOCK + normalize(username));
            long ipTtl = ip == null || maxIpFailures <= 0 ? 0 : ttl(IP_LOCK + ip);
            return Math.max(userTtl, ipTtl);
        } catch (RuntimeException e) {
            log.warn("登录频控读取失败，按未锁定放行: {}", e.getMessage());
            return 0;
        }
    }

    /** 记录一次失败；达到阈值时写入锁定键。 */
    public void recordFailure(String username, String ip) {
        try {
            String user = normalize(username);
            if (bump(USER_FAIL + user) >= maxFailures) {
                redis.opsForValue().set(USER_LOCK + user, "1", lock);
                redis.delete(USER_FAIL + user);
                log.warn("登录失败次数过多，锁定账号 {} 分钟: username={}, ip={}", lock.toMinutes(), user, ip);
            }
            if (ip != null && maxIpFailures > 0 && bump(IP_FAIL + ip) >= maxIpFailures) {
                redis.opsForValue().set(IP_LOCK + ip, "1", lock);
                redis.delete(IP_FAIL + ip);
                log.warn("登录失败次数过多，锁定 IP {} 分钟: ip={}", lock.toMinutes(), ip);
            }
        } catch (RuntimeException e) {
            log.warn("登录频控写入失败，忽略: {}", e.getMessage());
        }
    }

    /** 登录成功：清掉该用户名的失败计数。 */
    public void recordSuccess(String username) {
        try {
            redis.delete(USER_FAIL + normalize(username));
        } catch (RuntimeException e) {
            log.warn("登录频控清理失败，忽略: {}", e.getMessage());
        }
    }

    private long bump(String key) {
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redis.expire(key, window);
        }
        return n == null ? 0 : n;
    }

    private long ttl(String key) {
        Long seconds = redis.getExpire(key);
        return seconds == null || seconds < 0 ? 0 : seconds;
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
