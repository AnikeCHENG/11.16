package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.PaymentRecord;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.CreditEvent;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.PaymentRecordRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.PaymentService.PayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单领域服务：负责订单状态机、支付截止时间与收银台视图组装。
 *
 * <p>状态流转约束集中在此，Controller 只做鉴权与参数校验：</p>
 * <ul>
 *   <li>仅 {@link OrderStatus#PENDING_PAYMENT} 可支付，且未超过 {@code expire_time}</li>
 *   <li>仅 {@link OrderStatus#PENDING_PAYMENT} 可取消（已支付不允许取消）</li>
 *   <li>取消必须把商品恢复为在售，否则商品会被永久锁定</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final DateTimeFormatter ORDER_NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** data URL 中分隔元信息与 base64 载荷的标记，见 {@link #firstImage} */
    private static final String BASE64_MARKER = "base64,";

    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final OrderFeeService orderFeeService;
    private final FundService fundService;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;

    /** 支付有效期（分钟）。收银台倒计时、支付校验、定时任务三处共用该口径。 */
    private final int payTimeoutMinutes;

    /**
     * 信用分服务：可选注入。
     *
     * <p>走 setter 而不是构造器，是为了不动既有测试里
     * {@code new OrderService(...)} 的 10 参写法；为 null 时静默跳过记分。</p>
     */
    private CreditService creditService;

    @Autowired(required = false)
    void setCreditService(CreditService creditService) {
        this.creditService = creditService;
    }

    /**
     * 记一次信用事件。
     *
     * <p>{@link CreditService} 自身不抛异常，这里的 null 判断只是为了让
     * 未注入该 Bean 的单元测试照常运行。</p>
     */
    private void recordCredit(Long userId, CreditEvent event, Long bizId, String remark) {
        if (creditService != null) {
            creditService.record(userId, event, bizId, remark);
        }
    }

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        PaymentRecordRepository paymentRecordRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository,
                        ProductService productService,
                        OrderFeeService orderFeeService,
                        FundService fundService,
                        PaymentService paymentService,
                        TransactionTemplate transactionTemplate,
                        @Value("${order.pay-timeout-minutes:30}") int payTimeoutMinutes) {
        this.orderRepository = orderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.orderFeeService = orderFeeService;
        this.fundService = fundService;
        this.paymentService = paymentService;
        this.transactionTemplate = transactionTemplate;
        this.payTimeoutMinutes = payTimeoutMinutes;
    }

    public int getPayTimeoutMinutes() {
        return payTimeoutMinutes;
    }

    public String getPaymentChannel() {
        return paymentService.channelName();
    }

    /**
     * 业务异常：由 Controller 转换为 400 响应。
     */
    public static class OrderBusinessException extends RuntimeException {
        public OrderBusinessException(String message) {
            super(message);
        }
    }

    /**
     * 生成订单编号：yyyyMMddHHmmss + 3 位随机数。
     *
     * <p>order_no 上有唯一索引，同秒并发下单有极小概率撞键，
     * 此时重新生成一次即可。</p>
     */
    private String generateOrderNo() {
        return ORDER_NO_FORMAT.format(LocalDateTime.now())
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * 创建订单并锁定商品。
     *
     * @param productId 商品 ID
     * @param buyerId   买家 ID
     * @return 已落库并回填主键的订单
     */
    @Transactional
    public Order createOrder(Long productId, Long buyerId) {
        Product product = productRepository.selectById(productId);
        if (product == null) {
            throw new OrderBusinessException("商品不存在");
        }
        // 仅在售商品允许下单（语义以 ProductStatus 为准）
        if (product.getStatus() == null || ProductStatus.isSold(product.getStatus())) {
            throw new OrderBusinessException("商品已售出");
        }
        if (ProductStatus.isOffShelf(product.getStatus())) {
            throw new OrderBusinessException("商品已下架");
        }
        if (product.getUserId() != null && product.getUserId().equals(buyerId)) {
            throw new OrderBusinessException("不能购买自己的商品");
        }
        // 信用分过低限制下单。放在锁定商品之前，被拒时不会产生任何写入；
        // 查询失败时 CreditService 按"未受限"处理，不会误伤正常用户
        if (creditService != null && creditService.isRestricted(buyerId)) {
            throw new OrderBusinessException("你的信用分过低，暂时无法下单");
        }

        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setProductId(productId);
        order.setOrderTitle(product.getTitle());
        order.setOrderImage(firstImage(product.getImages()));
        order.setBuyerId(buyerId);
        order.setSellerId(product.getUserId() != null ? product.getUserId() : buyerId);
        order.setPrice(nz(product.getPrice()));
        // 校内自提免运费，保留字段以便扩展
        order.setShippingFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setCreatedTime(now);
        order.setUpdatedTime(now);
        order.setExpireTime(now.plusMinutes(payTimeoutMinutes));

        // 服务费由卖家承担：买家实付不含服务费
        BigDecimal price = nz(product.getPrice());
        User seller = userRepository.selectById(order.getSellerId());
        boolean studentVerified = seller != null
                && seller.getIsStudentVerified() != null
                && seller.getIsStudentVerified() == 1;
        BigDecimal fee = orderFeeService.calculateServiceFee(price, studentVerified);
        order.setServiceFee(fee);
        order.setSellerIncome(price.add(order.getShippingFee()).subtract(fee)
                .setScale(2, RoundingMode.HALF_UP));

        // 抢占商品：条件 UPDATE，status 必须仍是「在售」才能改成「已售出」。
        // 判断与写入合并在同一条 SQL，两个买家同时下单时只有一个能拿到 1 行，
        // 另一个拿到 0 行并在此被拦下——二手商品只有一份，绝不能出两张订单。
        // 本方法带 @Transactional，若随后的 insert 失败，这次锁定会一并回滚。
        if (!productService.markAsSoldIfOnSale(productId)) {
            throw new OrderBusinessException("商品已被抢先购买");
        }

        try {
            orderRepository.insert(order);
        } catch (DuplicateKeyException e) {
            if (!isOrderNoConflict(e)) {
                throw translateNonOrderNoConflict(e);
            }
            // 只有订单号撞键才值得重试：order_no = 秒级时间戳 + 3 位随机数，
            // 同一秒内的并发下单有极小概率撞车，换一个号即可
            order.setOrderNo(generateOrderNo());
            try {
                orderRepository.insert(order);
            } catch (DuplicateKeyException retryFailure) {
                if (!isOrderNoConflict(retryFailure)) {
                    throw translateNonOrderNoConflict(retryFailure);
                }
                log.warn("订单号连续两次撞键，放弃本次下单: orderNo={}", order.getOrderNo());
                throw new OrderBusinessException("下单失败，请稍后重试");
            }
        }

        return order;
    }

    /**
     * 判断唯一键冲突是否发生在订单号上。
     *
     * <p>MySQL 的报错形如
     * {@code Duplicate entry '20261012000816321' for key 'orders.uk_order_no'}，
     * 里面带着索引名，据此区分"订单号撞车"与"商品重复下单"。</p>
     *
     * <p><b>识别不出来时一律当作"不是订单号冲突"</b>：宁可少重试一次，
     * 也不要拿无意义的重试去掩盖真实的约束冲突——那会让同一个错误连报两次、
     * 日志里还看不到真正的原因。</p>
     */
    private static boolean isOrderNoConflict(DuplicateKeyException e) {
        String message = mostSpecificMessage(e);
        return message != null && message.contains("order_no");
    }

    /**
     * 把"不是订单号撞键"的唯一键冲突翻译成用户看得懂的业务异常。
     *
     * <p>区分两种：已知的商品唯一约束
     * （{@code uk_active_product}，见 migration-order-unique-active.sql）
     * 给明确提示；其余未知约束只回笼统话术并打日志——把没识别出的
     * 数据库错误硬说成"商品已有订单"会误导排查方向。</p>
     */
    private OrderBusinessException translateNonOrderNoConflict(DuplicateKeyException e) {
        String message = mostSpecificMessage(e);
        if (message != null && message.contains("active_product")) {
            return new OrderBusinessException("该商品已有未完成的订单，请勿重复下单");
        }
        log.error("下单时发生未识别的唯一键冲突", e);
        return new OrderBusinessException("下单失败，请稍后重试");
    }

    private static String mostSpecificMessage(DuplicateKeyException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause != null ? cause.getMessage() : e.getMessage();
    }

    /**
     * 惰性过期处理：若订单仍待支付但已过支付截止时间，则自动取消并释放商品。
     *
     * <p>不依赖定时器，因此即使用户关闭浏览器未点取消，商品也不会被永久锁定。</p>
     *
     * <p><b>要 {@code @Transactional}：</b>唯一调用方是
     * {@code OrderController} 的 {@code GET /orders/{id}}，走代理，
     * 注解真实生效；只有 {@code payOrder} 是同类内部调用（它直接调
     * {@link #doCancel} 而非本方法），无需在这里考虑。
     * 取消订单状态与释放商品必须原子——只改状态会让商品永久停在
     * 「已售出」，之后无人能再购买；信用分流水与改分也一并落在这个事务里。</p>
     *
     * @return 若本次调用触发了自动取消则返回 true
     */
    @Transactional
    public boolean autoCancelIfExpired(Order order) {
        if (!OrderStatus.isPendingPayment(order.getStatus()) || !isExpired(order)) {
            return false;
        }
        return doCancel(order, true);
    }

    /**
     * 执行取消：改状态并把商品恢复为在售。
     *
     * <p>两步都必须做。只改订单状态会让商品永久停在「已售出」，
     * 之后无人能再购买它。</p>
     *
     * <p><b>先条件改状态，成功后才释放商品。</b> 若状态已被并发请求推进
     * （典型是买家刚好支付成功），本次取消不得生效，更不得释放商品——
     * 否则会出现「钱已收、货重新上架」的双重损失。</p>
     *
     * @param byTimeout true 表示由超时驱动（定时任务/支付超时/惰性过期），
     *                  只有这种情况才扣买家信用分；买家主动取消不扣，
     *                  否则等于惩罚正常的反悔
     * @return true 表示确实由本次调用完成了取消
     */
    private boolean doCancel(Order order, boolean byTimeout) {
        int updated = orderRepository.updateStatusIfMatch(order.getId(),
                OrderStatus.PENDING_PAYMENT.getCode(), OrderStatus.CANCELLED.getCode());
        if (updated == 0) {
            return false;
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setUpdatedTime(LocalDateTime.now());
        productService.markAsOnSale(order.getProductId());
        if (byTimeout) {
            recordCredit(order.getBuyerId(), CreditEvent.ORDER_TIMEOUT, order.getId(),
                    "订单 " + order.getOrderNo() + " 超时未支付");
        }
        return true;
    }

    /** 判断订单是否已过支付截止时间；未设置 expire_time 的历史订单视为未过期 */
    public boolean isExpired(Order order) {
        return order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now());
    }

    /**
     * 支付订单。全流程一个事务。
     *
     * <p><b>关键约束：业务失败一律通过返回值表达，绝不抛异常。</b>
     * 「超时取消」分支会先执行取消（含释放商品）再返回失败对象；
     * 若改为抛异常，异常将传播出事务边界导致整个事务回滚，
     * 取消与商品释放全部失效，商品被永久锁死在「已售出」。</p>
     *
     * <p>支付渠道由 {@link PaymentService} 抽象，本方法不感知具体渠道；
     * 渠道失败时直接返回其失败结果，不写入任何数据。</p>
     *
     * @param orderId   订单主键
     * @param payMethod 支付方式，原样记录（余额/支付宝/微信，不做白名单校验）
     * @param userId    当前登录用户，必须是买家
     */
    @Transactional
    public PayResult payOrder(Long orderId, String payMethod, Long userId) {
        Order order = requireOrder(orderId);

        // 归属校验：只有买家可支付
        if (order.getBuyerId() == null || !order.getBuyerId().equals(userId)) {
            return PayResult.fail(403, "只有买家可以支付该订单");
        }
        if (payMethod == null || payMethod.isBlank()) {
            return PayResult.fail("请选择支付方式");
        }

        // 过期检查：先走取消逻辑并释放商品，再返回失败（不可抛异常，见方法注释）
        if (isExpired(order)) {
            boolean cancelled = OrderStatus.isPendingPayment(order.getStatus()) && doCancel(order, true);
            if (cancelled) {
                log.info("订单支付超时已自动取消: orderNo={}", order.getOrderNo());
                return PayResult.fail("订单已超时取消");
            }
            // 取消没生效：状态已被并发请求推进，此时不能宣称"已取消"，如实让用户刷新
            log.info("订单超时但状态已变化，取消未生效: orderNo={}", order.getOrderNo());
            return PayResult.fail("订单状态已变化，请刷新后重试");
        }

        // 状态守卫：防止对同一订单重复支付
        if (!OrderStatus.isPendingPayment(order.getStatus())) {
            return PayResult.fail("当前订单状态不可支付");
        }

        // ============ 支付：先占位，再调渠道 ============
        //
        // 顺序不能反。原实现是「先调渠道扣款 → 再条件更新状态」，
        // 于是渠道已扣钱、状态更新失败（并发取消/重复提交）时会出现
        // 「钱扣了订单却没推进」；接入真实渠道后这就是重复扣款与人工退款。
        //
        // 现在改为：
        //   1) 预生成幂等键（渠道可用时用渠道格式，否则本地生成）
        //   2) 条件更新把订单从「待支付」推进为「待发货」并写入幂等键 —— 这是占位。
        //      占位失败 = 订单已被并发改走，直接失败，**根本不会调用渠道**，所以不会重复扣款。
        //   3) 占位成功后才调渠道；渠道按幂等键去重，重试也只扣一次。
        //   4) 渠道失败 → 条件回滚成「待支付」（钱没扣，订单必须回到未支付态）。

        LocalDateTime now = LocalDateTime.now();
        String idempotencyKey = resolveIdempotencyKey(order);

        // 条件更新：只有订单当前仍是「待支付」才推进为「待发货」。
        // 改用 updateById 时，「刚被定时任务取消」或「重复提交」都会静默覆盖状态，
        // 于是已取消的订单又变成待发货，还多出一条流水。
        int updated = orderRepository.markPaidIfPending(order.getId(),
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PENDING_SHIPMENT.getCode(),
                payMethod, idempotencyKey);
        if (updated == 0) {
            // 状态已被并发请求改走（超时取消/重复提交）：
            // 本次不调渠道、不记流水，让用户刷新后看到真实状态
            log.warn("支付占位失败，订单状态已变化: orderNo={}", order.getOrderNo());
            return PayResult.fail("订单状态已变化，本次未扣款，请刷新后重试");
        }

        // 占位成功，把幂等键挂到订单上：渠道据此去重
        order.setTransactionId(idempotencyKey);

        // 委托支付渠道（Mock 实现永远成功）
        PayResult channelResult = paymentService.pay(order, payMethod);
        if (!channelResult.isSuccess()) {
            // 渠道失败 = 钱没扣，订单必须退回待支付，否则用户看到「已支付」却没货
            int reverted = orderRepository.revertToPendingIfPaid(order.getId(),
                    OrderStatus.PENDING_SHIPMENT.getCode(),
                    OrderStatus.PENDING_PAYMENT.getCode());
            log.warn("支付渠道失败，已回滚订单状态: orderNo={}, reverted={}, reason={}",
                    order.getOrderNo(), reverted, channelResult.getMessage());
            return channelResult;
        }

        // 余额支付走平台内部账本：条件 UPDATE 扣款 + OUT 流水。
        // 余额不足时抛异常，让上面的「已支付」状态更新一起回滚；
        // 第三方渠道（Mock 支付宝/微信）不经过这里。
        if (FundService.isBalanceMethod(payMethod)) {
            BigDecimal payable = nz(order.getPrice())
                    .add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            fundService.debitForPurchase(order, userId, payable);
        }

        // 回填内存对象，供调用方（收银台响应）直接展示
        order.setStatus(OrderStatus.PENDING_SHIPMENT.getCode());
        order.setPaymentMethod(payMethod);
        order.setPaidTime(now);
        order.setUpdatedTime(now);
        // 交易号以幂等键为准：占位时写进库的就是它，渠道也按它去重，
        // 三处（orders.transaction_id / payment_record.trade_no / 渠道）必须一致
        String tradeNo = channelResult.getTradeNo() != null
                ? channelResult.getTradeNo()
                : idempotencyKey;
        order.setTransactionId(tradeNo);

        // 落支付流水。amount 为买家实付（订单价 + 运费），不含卖家承担的服务费
        paymentRecordRepository.insert(buildPaymentRecord(order, payMethod, tradeNo, now));

        log.info("支付成功: orderNo={}, payMethod={}, tradeNo={}",
                order.getOrderNo(), payMethod, tradeNo);
        return PayResult.ok(tradeNo);
    }

    /**
     * 取本次支付的幂等键。
     *
     * <p>优先用渠道生成的（保持渠道的流水号格式，如 Mock 的 {@code MOCK...}）；
     * 渠道不提供时退回到本地生成，保证任何渠道实现下都有幂等键可用。</p>
     *
     * @param order 待支付订单
     * @return 幂等键，永不为 {@code null}
     */
    private String resolveIdempotencyKey(Order order) {
        String key = null;
        try {
            key = paymentService.generateIdempotencyKey(order);
        } catch (Exception e) {
            log.warn("渠道生成幂等键失败，退回本地生成: orderNo={}", order.getOrderNo());
        }
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        // 本地兜底：时间戳 + 随机数，保证同一笔订单重试时不会变
        long entropy = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(100_000_000_000L, 1_000_000_000_000L);
        return "PAY" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMddHHmmssSSS")) + entropy;
    }

    private PaymentRecord buildPaymentRecord(Order order, String payMethod,
                                             String tradeNo, LocalDateTime now) {
        BigDecimal amount = nz(order.getPrice())
                .add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setAmount(amount);
        record.setPayMethod(payMethod);
        record.setTradeNo(tradeNo);
        record.setCreateTime(now);
        return record;
    }

    /**
     * 取消订单并把商品恢复为在售。
     *
     * <p>已支付的订单不允许取消（需走退款流程），避免资金已收却释放商品。</p>
     *
     * <p><b>买家主动取消不扣信用分</b>，扣分只针对超时未支付
     * （见 {@link #doCancel} 的 byTimeout 参数）。</p>
     *
     * <p>加 {@code @Transactional}：取消现在包含「改订单状态、恢复商品、
     * 记信用」多步写入，中途失败会让商品卡在「已售出」，
     * 这是此前遗留的问题。</p>
     */
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = requireOrder(orderId);
        if (!OrderStatus.isPendingPayment(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不允许取消");
        }
        if (!doCancel(order, false)) {
            // 上面的检查与这里的 UPDATE 之间被并发请求插队（典型是刚好支付成功）
            throw new OrderBusinessException("订单状态已变化，取消失败，请刷新后重试");
        }
        return order;
    }

    /**
     * 卖家发货：待发货（1）→ 待收货（2）。
     *
     * <p>此前项目里没有这两个端点，前端"确认收货"走的是通用的
     * {@code PUT /api/orders/{id}}——它只校验调用者是买卖双方之一，
     * 于是买家可以把待支付订单直接改成待收货、卖家可以替买家确认收货，
     * 状态机形同虚设。现在每一步都要求<b>特定角色 + 特定前置状态</b>。</p>
     *
     * @param orderId 订单主键
     * @param userId  当前登录用户，必须是该订单的卖家
     */
    @Transactional
    public Order shipOrder(Long orderId, Long userId) {
        Order order = requireOrder(orderId);
        if (order.getSellerId() == null || !order.getSellerId().equals(userId)) {
            throw new OrderBusinessException("只有卖家可以发货");
        }
        int updated = orderRepository.markShippedIfPending(orderId,
                OrderStatus.PENDING_SHIPMENT.getCode(), OrderStatus.PENDING_RECEIPT.getCode());
        if (updated == 0) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不允许发货");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.PENDING_RECEIPT.getCode());
        order.setShippedTime(now);
        order.setUpdatedTime(now);
        log.info("卖家 {} 发货: orderNo={}", userId, order.getOrderNo());
        return order;
    }

    /**
     * 买家确认收货：待收货（2）→ 已完成（3），并在<b>同一事务</b>内给卖家入账。
     *
     * <p>入账（{@link FundService#creditSellerIncome}）与状态推进共用一个事务：
     * 状态改了钱没记，卖家白发货；钱记了状态没改，订单能再确认一次。
     * 两者要么都成，要么都不成。</p>
     *
     * @param orderId 订单主键
     * @param userId  当前登录用户，必须是该订单的买家
     */
    @Transactional
    public Order confirmReceipt(Long orderId, Long userId) {
        Order order = requireOrder(orderId);
        if (order.getBuyerId() == null || !order.getBuyerId().equals(userId)) {
            throw new OrderBusinessException("只有买家可以确认收货");
        }
        int updated = orderRepository.markCompletedIfPending(orderId,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode());
        if (updated == 0) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不允许确认收货");
        }
        completeOrder(order);
        log.info("买家 {} 确认收货: orderNo={}", userId, order.getOrderNo());
        return order;
    }

    /**
     * 订单进入「已完成」后的共同收尾：状态、放款、双方信用分。
     *
     * <p>买家手动确认、发货后自动确认、管理员标记完成三条路径共用。
     * 必须是调用方事务的一部分：状态改了钱没记，卖家白发货；钱记了状态没改，
     * 订单能再确认一次。</p>
     *
     * <p>注意 {@code completed_time} 已由 {@code markCompletedIfPending} 在 SQL 里
     * 写入数据库；这里同步内存对象只是为了让当次响应里的时间与库里一致，
     * 真正持久化的是那条 SQL。</p>
     */
    private void completeOrder(Order order) {
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.COMPLETED.getCode());
        if (order.getCompletedTime() == null) {
            order.setCompletedTime(now);
        }
        order.setUpdatedTime(now);

        // 放款：seller_income 计入卖家余额 + 写资金流水（表结构见
        // migration-seller-balance.sql）。任一步失败都会连同上面的状态更新一起回滚。
        fundService.creditSellerIncome(order);

        // 双方各 +2。放在放款之后：放款失败整体回滚时不会留下信用记录。
        // 唯一键（user + ORDER_COMPLETED + orderId）保证重复确认收货不会重复加分。
        recordCredit(order.getBuyerId(), CreditEvent.ORDER_COMPLETED, order.getId(),
                "完成订单 " + order.getOrderNo());
        recordCredit(order.getSellerId(), CreditEvent.ORDER_COMPLETED, order.getId(),
                "完成订单 " + order.getOrderNo());
    }

    /**
     * 买家申请退款：仅「待发货/待收货」订单。
     *
     * <p>确认收货前卖家尚未入账（放款发生在 {@link #completeOrder}），
     * 因此退款不需要从卖家余额扣回；完成后订单不允许再走退款。</p>
     */
    @Transactional
    public Order applyRefund(Long orderId, Long buyerId, String reason) {
        Order order = requireOrder(orderId);
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new OrderBusinessException("只有买家可以申请退款");
        }
        if (!isRefundableStatus(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不允许申请退款");
        }

        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new OrderBusinessException("请填写退款原因");
        }
        if (cleanReason.length() > 100) {
            throw new OrderBusinessException("退款原因不能超过 100 个字符");
        }

        int previousStatus = order.getStatus();
        int updated = orderRepository.markRefundingIfPaid(orderId,
                OrderStatus.REFUNDING.getCode(),
                OrderStatus.PENDING_SHIPMENT.getCode(),
                OrderStatus.PENDING_RECEIPT.getCode(),
                cleanReason);
        if (updated == 0) {
            throw new OrderBusinessException("订单状态已变化，申请退款失败，请刷新后重试");
        }

        order.setStatus(OrderStatus.REFUNDING.getCode());
        order.setRefundFromStatus(previousStatus);
        order.setRefundReason(cleanReason);
        order.setRefundApplyTime(LocalDateTime.now());
        order.setRefundAmount(nz(order.getPrice())
                .add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP));
        order.setUpdatedTime(order.getRefundApplyTime());
        return order;
    }

    /** 卖家同意退款：退款中(5) → 已退款(6)，商品恢复在售 */
    @Transactional
    public Order approveRefund(Long orderId, Long sellerId, String reply) {
        Order order = requireOrder(orderId);
        if (order.getSellerId() == null || !order.getSellerId().equals(sellerId)) {
            throw new OrderBusinessException("只有卖家可以处理退款申请");
        }
        return doApproveRefund(order, reply);
    }

    /** 卖家拒绝退款：退款中(5) → 申请前状态 */
    @Transactional
    public Order rejectRefund(Long orderId, Long sellerId, String reply) {
        Order order = requireOrder(orderId);
        if (order.getSellerId() == null || !order.getSellerId().equals(sellerId)) {
            throw new OrderBusinessException("只有卖家可以处理退款申请");
        }
        return doRejectRefund(order, reply);
    }

    /** 买家撤销退款申请：退款中(5) → 申请前状态 */
    @Transactional
    public Order cancelRefundRequest(Long orderId, Long buyerId) {
        Order order = requireOrder(orderId);
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new OrderBusinessException("只有买家可以撤销退款申请");
        }
        return doRejectRefund(order, "买家主动撤销退款申请");
    }

    /** 管理员强制退款：1/2 直接退，或同意已提交的 5 */
    @Transactional
    public Order adminRefund(Long orderId, String reason, String reply) {
        Order order = requireOrder(orderId);
        if (OrderStatus.isRefunding(order.getStatus())) {
            return doApproveRefund(order, reply);
        }
        if (!isRefundableStatus(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不允许退款");
        }

        String cleanReason = reason == null || reason.isBlank()
                ? "管理员操作退款" : reason.trim();
        String cleanReply = reply == null || reply.isBlank()
                ? "管理员同意退款" : reply.trim();
        int updated = orderRepository.markRefundedIfPaid(orderId,
                OrderStatus.REFUNDED.getCode(),
                OrderStatus.PENDING_SHIPMENT.getCode(),
                OrderStatus.PENDING_RECEIPT.getCode(),
                cleanReason, cleanReply);
        if (updated == 0) {
            throw new OrderBusinessException("订单状态已变化，退款失败，请刷新后重试");
        }

        int previousStatus = order.getStatus();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.REFUNDED.getCode());
        order.setRefundFromStatus(previousStatus);
        order.setRefundReason(cleanReason);
        order.setRefundReply(cleanReply);
        order.setRefundApplyTime(now);
        order.setRefundTime(now);
        order.setRefundAmount(nz(order.getPrice())
                .add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP));
        order.setUpdatedTime(now);
        productService.markAsOnSale(order.getProductId());
        // 余额支付的订单把钱退回买家余额；第三方渠道由渠道侧处理（Mock 仅改状态）
        fundService.refundToBuyer(order, order.getRefundAmount());
        log.info("管理员退款: orderNo={}, amount={}", order.getOrderNo(), order.getRefundAmount());
        return order;
    }

    /** 管理员拒绝退款申请：仅退款中(5) → 申请前状态 */
    @Transactional
    public Order adminRejectRefund(Long orderId, String reply) {
        Order order = requireOrder(orderId);
        return doRejectRefund(order, reply);
    }

    /** 管理员取消待支付订单：仅 0 → 4，并释放商品 */
    @Transactional
    public Order adminCancelUnpaid(Long orderId) {
        Order order = requireOrder(orderId);
        if (!OrderStatus.isPendingPayment(order.getStatus())) {
            throw new OrderBusinessException("仅待支付订单可以取消；已支付订单请走退款");
        }
        if (!doCancel(order, false)) {
            throw new OrderBusinessException("订单状态已变化，取消失败，请刷新后重试");
        }
        log.info("管理员取消待支付订单: orderNo={}", order.getOrderNo());
        return order;
    }

    /** 管理员直接完成订单：仅 2 → 3，与买家确认收货走同一套放款逻辑 */
    @Transactional
    public Order adminComplete(Long orderId) {
        Order order = requireOrder(orderId);
        int updated = orderRepository.markCompletedIfPending(orderId,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode());
        if (updated == 0) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不允许标记完成");
        }
        completeOrder(order);
        log.info("管理员完成订单: orderNo={}", order.getOrderNo());
        return order;
    }

    private Order doApproveRefund(Order order, String reply) {
        if (!OrderStatus.isRefunding(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不是退款中");
        }
        String cleanReply = reply == null || reply.isBlank() ? "同意退款" : reply.trim();
        int updated = orderRepository.markRefundedIfRefunding(order.getId(),
                OrderStatus.REFUNDING.getCode(),
                OrderStatus.REFUNDED.getCode(),
                cleanReply);
        if (updated == 0) {
            throw new OrderBusinessException("退款状态已变化，请刷新后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.REFUNDED.getCode());
        order.setRefundReply(cleanReply);
        order.setRefundTime(now);
        order.setUpdatedTime(now);
        if (order.getRefundAmount() == null) {
            order.setRefundAmount(nz(order.getPrice())
                    .add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        // 商品恢复在售：与取消订单同一口径。若商品已被删除，markAsOnSale 会安全返回 false。
        productService.markAsOnSale(order.getProductId());
        // 余额支付的订单把钱退回买家余额；第三方渠道由渠道侧处理（Mock 仅改状态）
        fundService.refundToBuyer(order, order.getRefundAmount());
        log.info("退款完成: orderNo={}, reply={}", order.getOrderNo(), cleanReply);
        return order;
    }

    private Order doRejectRefund(Order order, String reply) {
        if (!OrderStatus.isRefunding(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态（"
                    + labelOf(order.getStatus()) + "）不是退款中");
        }
        if (order.getRefundFromStatus() == null) {
            throw new OrderBusinessException("退款申请缺少原状态，无法回退");
        }
        String cleanReply = reply == null || reply.isBlank() ? "拒绝退款" : reply.trim();
        int updated = orderRepository.markRefundRejected(order.getId(),
                OrderStatus.REFUNDING.getCode(), cleanReply);
        if (updated == 0) {
            throw new OrderBusinessException("退款状态已变化，请刷新后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(order.getRefundFromStatus());
        order.setRefundReply(cleanReply);
        order.setRefundTime(now);
        order.setUpdatedTime(now);
        log.info("退款申请被驳回: orderNo={}, backTo={}", order.getOrderNo(), order.getStatus());
        return order;
    }

    private static boolean isRefundableStatus(Integer status) {
        return OrderStatus.fromCode(status) == OrderStatus.PENDING_SHIPMENT
                || OrderStatus.fromCode(status) == OrderStatus.PENDING_RECEIPT;
    }

    /**
     * 扫描并取消所有超时未支付的订单（供定时任务调用）。
     *
     * <p>逐单独立事务：单条订单失败（如下架商品已被删除）不应影响其余订单，
     * 否则整批回滚会造成部分订单被重复扫描、迟迟无法释放。</p>
     *
     * <p>与 {@link #autoCancelIfExpired} 是互补关系而非重复：
     * 定时任务负责用户完全离开后的兜底释放（最长延迟一个扫描周期），
     * 惰性检查负责扫描间隙内用户访问收银台时立即释放。两者同时存在，
     * 商品被锁定的窗口时间才趋近于零。</p>
     *
     * @return 本次成功取消的订单数
     */
    public int cancelTimeoutOrdersBatch() {
        LocalDateTime now = LocalDateTime.now();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("status", OrderStatus.PENDING_PAYMENT.getCode())
                .isNotNull("expire_time")
                .lt("expire_time", now);
        List<Order> expired = orderRepository.selectList(wrapper);
        if (expired.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        for (Order order : expired) {
            try {
                final Long orderId = order.getId();
                Boolean done = transactionTemplate.execute(status -> {
                    Order fresh = orderRepository.selectById(orderId);
                    // 事务内重新确认状态，避免与用户支付并发导致误取消已支付订单
                    if (fresh == null || !OrderStatus.isPendingPayment(fresh.getStatus())
                            || !isExpired(fresh)) {
                        return false;
                    }
                    // 条件 UPDATE 才是真正的判据：selectById 之后、UPDATE 之前
                    // 买家仍可能支付成功，那时 doCancel 返回 false，本次不计数
                    return doCancel(fresh, true);
                });
                if (Boolean.TRUE.equals(done)) {
                    cancelled++;
                }
            } catch (Exception e) {
                log.error("超时取消订单失败: orderId={}, 原因={}", order.getId(), e.getMessage(), e);
            }
        }
        if (cancelled > 0) {
            log.info("定时任务取消超时订单 {} 笔", cancelled);
        }
        return cancelled;
    }

    /**
     * 自动确认收货：卖家发货超过 {@code days} 天仍未被买家确认的订单，
     * 直接置为已完成并放款。
     *
     * <p>没有这一步时，买家只要不点「确认收货」，卖家的钱就永远停在平台侧；
     * 二手交易没有物流签收记录，无法用真实签收时间兜底，只能按发货时间计算。</p>
     *
     * <p>逐单独立事务：单笔失败不能影响其余订单。事务内重新读取并再次校验
     * 状态/时间，避免扫描后、执行前买家刚好确认或申请退款。</p>
     *
     * @param days 发货后多少天自动确认；{@code <=0} 表示关闭
     * @return 本次自动完成的订单数
     */
    public int autoConfirmReceiptBatch(int days) {
        if (days <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("status", OrderStatus.PENDING_RECEIPT.getCode())
                .isNotNull("shipped_time")
                .lt("shipped_time", cutoff);
        List<Order> due = orderRepository.selectList(wrapper);
        if (due.isEmpty()) {
            return 0;
        }

        int completed = 0;
        for (Order order : due) {
            try {
                final Long orderId = order.getId();
                Boolean done = transactionTemplate.execute(status -> {
                    Order fresh = orderRepository.selectById(orderId);
                    if (fresh == null
                            || OrderStatus.fromCode(fresh.getStatus()) != OrderStatus.PENDING_RECEIPT
                            || fresh.getShippedTime() == null
                            || !fresh.getShippedTime().isBefore(cutoff)) {
                        return false;
                    }
                    int updated = orderRepository.markCompletedIfPending(orderId,
                            OrderStatus.PENDING_RECEIPT.getCode(),
                            OrderStatus.COMPLETED.getCode());
                    if (updated == 0) {
                        return false;
                    }
                    completeOrder(fresh);
                    return true;
                });
                if (Boolean.TRUE.equals(done)) {
                    completed++;
                }
            } catch (Exception e) {
                log.error("自动确认收货失败: orderId={}, 原因={}", order.getId(), e.getMessage(), e);
            }
        }
        if (completed > 0) {
            log.info("自动确认收货 {} 笔（发货超过 {} 天）", completed, days);
        }
        return completed;
    }

    /** 组装收银台所需的全部交易要素 */
    public Map<String, Object> buildCashierView(Order order) {
        Map<String, Object> view = new LinkedHashMap<>();

        view.put("id", order.getId());
        view.put("orderNo", order.getOrderNo());
        view.put("status", order.getStatus());
        view.put("statusLabel", labelOf(order.getStatus()));

        BigDecimal price = nz(order.getPrice());
        BigDecimal shipping = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
        // 买家实付 = 商品金额 + 运费（服务费由卖家承担，不计入买家实付）
        BigDecimal total = price.add(shipping).setScale(2, RoundingMode.HALF_UP);

        view.put("price", price);
        view.put("shippingFee", shipping);
        view.put("serviceFee", order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO);
        view.put("sellerIncome", order.getSellerIncome());
        view.put("total", total);

        // 商品快照：优先用下单时的快照，缺失时回落实时查询（兼容迁移前数据）
        String title = order.getOrderTitle();
        String image = order.getOrderImage();
        if (title == null || image == null) {
            Product product = productRepository.selectById(order.getProductId());
            if (product != null) {
                if (title == null) {
                    title = product.getTitle();
                }
                if (image == null) {
                    image = firstImage(product.getImages());
                }
            }
        }
        Map<String, Object> productView = new LinkedHashMap<>();
        productView.put("id", order.getProductId());
        productView.put("title", title != null ? title : "商品已删除");
        productView.put("image", image);
        view.put("product", productView);

        // 卖家信息
        Map<String, Object> sellerView = new LinkedHashMap<>();
        User seller = userRepository.selectById(order.getSellerId());
        sellerView.put("id", order.getSellerId());
        sellerView.put("nickname", seller != null ? seller.getUsername() : "未知卖家");
        sellerView.put("avatar", seller != null ? seller.getAvatar() : null);
        sellerView.put("studentVerified", seller != null
                && seller.getIsStudentVerified() != null
                && seller.getIsStudentVerified() == 1);
        view.put("seller", sellerView);

        view.put("createdTime", order.getCreatedTime());
        view.put("expireTime", order.getExpireTime());
        view.put("paidTime", order.getPaidTime());
        view.put("shippedTime", order.getShippedTime());
        view.put("completedTime", order.getCompletedTime());
        view.put("paymentMethod", order.getPaymentMethod());
        view.put("transactionId", order.getTransactionId());
        view.put("refundFromStatus", order.getRefundFromStatus());
        view.put("refundReason", order.getRefundReason());
        view.put("refundReply", order.getRefundReply());
        view.put("refundApplyTime", order.getRefundApplyTime());
        view.put("refundTime", order.getRefundTime());
        view.put("refundAmount", order.getRefundAmount());
        view.put("remainSeconds", remainSeconds(order));
        return view;
    }

    /** 剩余支付秒数；非待支付或已过期返回 0 */
    public long remainSeconds(Order order) {
        if (!OrderStatus.isPendingPayment(order.getStatus()) || order.getExpireTime() == null) {
            return 0L;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), order.getExpireTime()).getSeconds();
        return Math.max(seconds, 0L);
    }

    public Order requireOrder(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        return order;
    }

    /**
     * 订单能否被物理删除。
     *
     * <p>两道门槛，缺一不可：</p>
     * <ol>
     *   <li><b>状态必须已终结</b>（已完成 / 已取消 / 已退款）。进行中的订单删掉
     *       等于抹掉交易凭证：商品已锁定、钱已收，却再也查不到这笔单子。</li>
     *   <li><b>不能有资金流水</b>。{@code account_flow} 与 {@code payment_record}
     *       通过 {@code order_id} 关联 {@code orders}，但没有外键也没有级联删除；
     *       已完成的订单早已放款给卖家，删掉订单就会留下「有流水无订单」的孤儿记录，
     *       对账时无法解释这笔钱冲哪笔单来的。</li>
     * </ol>
     *
     * @param order 订单
     * @return true 表示可以删除
     */
    public boolean canDelete(Order order) {
        if (order == null) {
            return false;
        }
        if (!OrderStatus.isTerminal(order.getStatus())) {
            return false;
        }
        return !fundService.hasFundFlow(order.getId());
    }

    private static String labelOf(Integer status) {
        OrderStatus s = OrderStatus.fromCode(status);
        return s != null ? s.getLabel() : "未知状态";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * 取多图列表中的第一张。
     *
     * <p>注意：{@code products.images} 存的是 base64 Data URL，其载荷内部<b>本身含逗号</b>
     * （{@code data:image/jpeg;base64,AAAA}），直接按第一个逗号切分会把 URL 拦腰截断成
     * {@code data:image/jpeg;base64}，图片彻底失效。base64 字母表为
     * {@code A-Za-z0-9+/=} 不含逗号，因此从 {@code base64,} 之后开始找下一个逗号，
     * 才是真正的多图分隔符。</p>
     */
    public static String firstImage(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        int payloadStart = images.indexOf(BASE64_MARKER);
        if (payloadStart < 0) {
            // 非 data URL，退回按逗号分隔
            int idx = images.indexOf(',');
            return idx > 0 ? images.substring(0, idx) : images;
        }
        int from = payloadStart + BASE64_MARKER.length();
        int sep = images.indexOf(',', from);
        return sep > 0 ? images.substring(0, sep) : images;
    }
}
