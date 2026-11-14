package com.example.campussecondhand.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 常见客户端错误不得伪装成 500。
 *
 * <p>背景：本项目的 {@code @ExceptionHandler(Exception.class)} 是兜底分支，
 * 会把任何未被专门处理的异常都变成 500 + 一整条堆栈。<b>方法不支持</b>与
 * <b>请求体不可解析</b>都属于客户端错误，特意单独处理。</p>
 *
 * <p>删除 {@code PUT /api/orders/{id}} 之后这一点变得很具体：旧前端
 * （浏览器里没刷新的那份）仍会对该路径发 PUT，如果返回 500，
 * 排查方向会完全跑偏；返回 405 才说得清"接口换了"。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("请求方法不支持 -> 405，而不是 500")
    void method_not_supported_maps_to_405() {
        var resp = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(405);
        assertThat(resp.getBody().getMessage()).contains("PUT");
    }

    @Test
    @DisplayName("上传超过大小上限 -> 413 带可读提示，而不是 500 + 堆栈")
    void upload_too_large_maps_to_413() {
        var resp = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(2 * 1024 * 1024));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(413);
        assertThat(resp.getBody().getMessage()).contains("大小上限");
    }

    /**
     * 403 刻意走 HTTP 200：本项目所有业务型 403（13 处 Controller）都写作
     * {@code ResponseEntity.ok(ApiResponse.error(403, ...))}，前端也一律
     * 先解析 JSON 再判断 {@code body.code}。这里改成真实 HTTP 403
     * 反而与约定不一致，且会让「HTTP 200 即请求已送达」的调用方多出分支。
     */
    @Test
    @DisplayName("业务禁止 -> HTTP 200 + body.code=403，且原样带出原因")
    void forbidden_maps_to_body_code_403() {
        var resp = handler.handleForbidden(new ForbiddenException("你的信用分过低，暂时无法发布商品"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(403);
        assertThat(resp.getBody().getMessage()).contains("信用分过低");
    }
}
