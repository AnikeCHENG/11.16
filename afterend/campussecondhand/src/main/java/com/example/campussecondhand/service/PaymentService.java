package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;

/**
 * 支付渠道抽象。
 *
 * <p>把「如何发起支付」从订单状态机中剥离，使订单服务不依赖任何具体渠道。
 * 当前仅有本地模拟实现 {@code MockPaymentServiceImpl}；
 * 将来接入支付宝/微信时只需新增一个实现类并修改配置项 {@code payment.channel}，
 * {@link OrderService} 的支付逻辑无需改动。</p>
 */
public interface PaymentService {

    /**
     * 发起支付。
     *
     * <p>实现方不应自行校验订单状态或超时——那属于订单状态机的职责，
     * 由 {@link OrderService} 在调用本方法之前完成校验。</p>
     *
     * @param order    待支付订单
     * @param payMethod 支付方式（余额/支付宝/微信），原样透传给渠道
     * @return 支付结果，永不为 {@code null}
     */
    PayResult pay(Order order, String payMethod);

    /**
     * 生成本次支付的<b>幂等键</b>。
     *
     * <p><b>为什么需要它</b>：订单状态推进与渠道扣款分属两个系统，没有分布式事务
     * 只能靠「幂等键 + 补偿」。正确顺序是<b>先占位（条件更新订单状态）、再调渠道</b>：
     * 占位失败说明订单已被并发改走，直接失败，根本不会调用渠道，因此不会重复扣款；
     * 占位成功后才发起扣款，渠道按这个键去重，即使调用重试也只会扣一次。</p>
     *
     * <p>默认返回 {@code null} 表示渠道不要求幂等键，调用方会退回到本地生成的键。</p>
     *
     * @param order 待支付订单
     * @return 幂等键；{@code null} 表示由调用方自行生成
     */
    default String generateIdempotencyKey(Order order) {
        return null;
    }

    /**
     * 渠道标识，用于日志与排查。
     */
    String channelName();

    /**
     * 支付结果。
     *
     * <p>刻意不使用异常表达「业务失败」：调用方 {@link OrderService} 的支付流程
     * 处于一个数据库事务中，若因渠道失败而抛异常，会连带回滚本事务内
     * 已写入的订单状态变更。返回失败对象可保证事务按预期提交或回滚。</p>
     */
    final class PayResult {

        private final boolean success;
        private final String tradeNo;
        private final String message;
        private final int code;

        private PayResult(boolean success, String tradeNo, String message, int code) {
            this.success = success;
            this.tradeNo = tradeNo;
            this.message = message;
            this.code = code;
        }

        /** 支付成功 */
        public static PayResult ok(String tradeNo) {
            return new PayResult(true, tradeNo, "支付成功", 200);
        }

        /** 业务失败（HTTP 200 + 业务错误码，与项目其余接口保持一致） */
        public static PayResult fail(String message) {
            return new PayResult(false, null, message, 400);
        }

        /** 业务失败，可指定业务码（403 未授权等） */
        public static PayResult fail(int code, String message) {
            return new PayResult(false, null, message, code);
        }

        public boolean isSuccess() {
            return success;
        }

        /** 渠道流水号；失败时为 {@code null} */
        public String getTradeNo() {
            return tradeNo;
        }

        public String getMessage() {
            return message;
        }

        /** 业务码，供 Controller 映射响应 */
        public int getCode() {
            return code;
        }
    }
}
