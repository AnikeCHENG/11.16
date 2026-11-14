package com.example.campussecondhand.config;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Set;

/**
 * C 端写请求的兜底鉴权拦截器。
 *
 * <p><b>为什么需要它：</b>{@code SecurityConfig} 是
 * {@code .anyRequest().permitAll()} + {@code csrf.disable()}，全站的登录校验
 * 依赖每个 Controller 方法自己解析 {@code Authorization} 头。只要新加一个写接口
 * 忘了写那几行，就是一个匿名可调用的裸奔端点——历史上
 * {@code POST /api/product/update-images} 正是这样清空过商品目录。</p>
 *
 * <p><b>判据：</b>所有 {@code /api/**} 的 POST/PUT/PATCH/DELETE 必须携带有效 JWT，
 * 且数据库中的用户仍然存在、未被封禁。只做「有没有登录」，不做业务级权限
 * （例如「只能改自己的商品」仍由各 Controller 判定），因此不会改变既有业务语义。</p>
 *
 * <p><b>为什么必须回查数据库：</b>JWT 是无状态的，封禁一个用户后他手里的旧 token
 * 在过期前依然有效。若只看签名，封禁动作要等 {@code jwt.expiration}（默认 24 小时）
 * 才真正生效。这里与 {@code AdminAuthInterceptor} 采用同一口径：
 * {@code status == 0} 视为封禁。</p>
 *
 * <p>放行白名单由 {@code WebMvcConfig} 用路径排除表达：{@code /api/auth/**}
 * （登录注册本身无身份）、{@code /api/dev/**}（仅 dev profile 存在）、
 * {@code /api/admin/**}（已由 {@code AdminAuthInterceptor} 做了更严格的管理员校验）。</p>
 */
@Component
public class AuthWriteInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod();
        if (method == null || !WRITE_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            // GET/HEAD/OPTIONS：CORS 预检与实际读请求都不在这里拦
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            return reject(response, HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }

        String username;
        try {
            username = jwtUtil.getUsernameFromToken(token);
        } catch (Exception e) {
            // getUsernameFromToken 内部已 catch 并返回 null，这里只防御实现变化
            username = null;
        }
        if (username == null) {
            return reject(response, HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return reject(response, HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        // status=0 为封禁；历史数据可能为 null，视为正常（与登录、管理端拦截器一致）
        if (user.getStatus() != null && user.getStatus() == 0) {
            return reject(response, HttpStatus.FORBIDDEN, "账户已被禁用");
        }
        return true;
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String message)
            throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(
                ApiResponse.error(status.value(), message)));
        return false;
    }
}
