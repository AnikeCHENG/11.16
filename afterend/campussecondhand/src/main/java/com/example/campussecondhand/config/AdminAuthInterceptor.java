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

/**
 * 管理端权限拦截器。
 *
 * <p>此前 {@code AdminController} 的 18 个端点各自手写
 * {@code if (!isAdmin(authHeader)) return 403}。这种方式有两个问题：
 * 新增端点时忘记写这一行就是越权漏洞；重复 18 处也难以保证判定逻辑始终一致。
 * 收敛到拦截器后，新端点自动受保护。</p>
 *
 * <p><b>角色以数据库为准，不以 JWT claim 为准。</b> token 里的 role 是签发那一刻的
 * 快照：只信 claim 的话，一个刚被降权或封禁的管理员，凭手里的旧 token
 * （默认 {@code jwt.expiration=86400}，整整 24 小时）仍然能调所有 {@code /api/admin/**}——
 * 封禁动作在 token 过期前形同虚设。因此这里解析出用户名后回查一次数据库，
 * 同时要求 {@code role == 1} 且未被封禁（{@code status != 0}）。</p>
 *
 * <p>前端 {@code /api/auth/me} 返回的也是数据库角色，二者由此对齐：
 * 守卫拦不拦、服务器放不放行，判的是同一件事。</p>
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 只读请求之外的动词（CORS 预检）不拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!isAdmin(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(MAPPER.writeValueAsString(
                    ApiResponse.error(403, "无权限：需要管理员身份")));
            return false;
        }
        return true;
    }

    private boolean isAdmin(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return false;
            }
            String token = header.substring(7);

            // 先验签解析，token 被篡改/过期时直接否掉，不必查库
            String username = jwtUtil.getUsernameFromToken(token);
            Integer claimRole = jwtUtil.getRoleFromToken(token);
            if (username == null || claimRole == null || claimRole != 1) {
                return false;
            }

            // 再回查数据库确认真实身份：降权、封禁、注销后旧 token 立即失效
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || user.getRole() == null || user.getRole() != 1) {
                return false;
            }
            // status=0 为封禁（与登录处的判定口径一致）；历史数据可能为 null，视为正常
            return user.getStatus() == null || user.getStatus() != 0;
        } catch (Exception e) {
            // token 过期、被篡改、查库失败等一律视为无权限，不向外抛异常细节
            return false;
        }
    }
}