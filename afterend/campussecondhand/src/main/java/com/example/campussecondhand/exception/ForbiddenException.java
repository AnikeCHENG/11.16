package com.example.campussecondhand.exception;

/**
 * 操作被业务规则禁止（能识别身份，但当前状态不允许做这件事）。
 *
 * <p>与 {@link BadRequestException} 的区别是语义：400 表示"请求本身有问题"，
 * 403 表示"请求没问题，但你不被允许"。典型场景是信用分低于 60 的用户
 * 发布商品——参数完全合法，只是规则上禁止。</p>
 *
 * <p>之所以需要这个异常类型：这条限制必须落在 {@code ProductService#create}
 * 这个唯一入口上，才能同时覆盖「商品发布」与「广场发卖货动态」两条路径，
 * 而 Service 层没有 HTTP 上下文，只能抛异常。详见
 * {@code GlobalExceptionHandler} 中对该异常的渲染方式。</p>
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
