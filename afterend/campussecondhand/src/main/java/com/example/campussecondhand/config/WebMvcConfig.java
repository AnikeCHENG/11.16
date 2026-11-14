package com.example.campussecondhand.config;

import com.example.campussecondhand.service.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：C 端写请求兜底鉴权、管理端鉴权拦截器 + 上传图片的静态资源映射。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AdminAuthInterceptor adminAuthInterceptor;

    @Autowired
    private AuthWriteInterceptor authWriteInterceptor;

    @Autowired
    private UploadService uploadService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");

        // C 端写接口的兜底鉴权。
        //
        // 管理端已有更严格的 AdminAuthInterceptor（角色 + 封禁），这里排除；
        // 认证接口本身就要在无身份状态下调用，也必须排除。
        // 之所以在拦截器层做，而不是继续依赖每个 Controller 手写解析：
        // 漏写一行就是一个匿名可调用的写接口，历史上已经发生过。
        registry.addInterceptor(authWriteInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**", "/api/auth/**", "/api/dev/**");
    }

    /**
     * 把 {@code /uploads/**} 映射到图片落盘目录（P1-12）。
     *
     * <p>数据库里存的是 {@code /uploads/2026/10/xxx.jpg} 这样的路径，需要一个地方
     * 把它变成真正的静态资源。用 {@code toUri()} 而不是拼字符串：Windows 的
     * 反斜杠与盘符拼进 {@code file:} 前缀很容易出问题，{@code toUri()} 会给出
     * {@code file:///E:/.../uploads/} 这种确定可用的形式。</p>
     *
     * <p><b>末尾斜杠不能省</b>：Spring 的资源解析是在 location 后面
     * {@code createRelative(path)} 拼相对路径的，location 不以 {@code /} 结尾时
     * 会拼成 {@code .../uploads2026/10/xxx.jpg}，结果每一个图片都 404。
     * 而 {@code Path.toUri()} 只在"该目录此刻已存在"时才会补斜杠——上传目录是
     * 首次上传时才创建的，启动那一刻它并不存在，所以这里必须显式补。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadService.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}