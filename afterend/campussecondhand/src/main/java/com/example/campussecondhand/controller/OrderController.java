package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.common.PageResult;
import java.util.Objects;
import java.util.stream.Collectors;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Review;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.service.ReviewService;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.OrderFeeService;
import com.example.campussecondhand.service.OrderService;
import com.example.campussecondhand.service.PaymentService.PayResult;
import com.example.campussecondhand.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderFeeService orderFeeService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JwtUtil jwtUtil;

    private Optional<User> getUserFromToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Optional.empty();
            }
            String token = authHeader.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

/**
     * 与我相关的订单（我买到的 + 我卖出的），物理分页。
     *
     * <p>商品与卖家信息按当页 id 批量补齐，避免每条订单两次 selectById 的 N+1；
     * 输出字段与改造前完全一致，前端契约不变。</p>
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<?>> getMyOrders(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        PageParam paging = PageParam.of(page, size);
        Long userId = userOpt.get().getId();

        IPage<Order> paged = orderRepository.selectPage(
                paging.toPage(),
                new QueryWrapper<Order>()
                        // OR 必须整体包进 and(...)，否则会泄漏到后续追加的条件之外
                        .and(q -> q.eq("buyer_id", userId).or().eq("seller_id", userId))
                        .orderByDesc("created_time", "id"));

        List<Map<String, Object>> orderList = toOrderViews(paged.getRecords());

        return ResponseEntity.ok(ApiResponse.success("获取成功",
                PageResult.of(orderList, paged.getTotal(), paging)));
    }

    /**
     * 订单列表项组装：批量取商品与卖家，字段保持与历史实现一致。
     *
     * <p>图片是 base64 Data URL，载荷内含逗号，必须用 firstImage 而非 split(",")。</p>
     */
    private List<Map<String, Object>> toOrderViews(List<Order> orders) {
        List<Long> productIds = orders.stream()
                .map(Order::getProductId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productRepository.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> sellerIds = orders.stream()
                .map(Order::getSellerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> sellers = sellerIds.isEmpty() ? Map.of()
                : userRepository.selectBatchIds(sellerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> orderMap = new HashMap<>();
            // id 必须是订单主键：前端后续用它在 /api/orders/{id} 上做支付/取消/收货
            orderMap.put("id", order.getId());
            orderMap.put("orderNo", order.getOrderNo());
            orderMap.put("productId", order.getProductId());

            Product product = products.get(order.getProductId());
            if (product != null) {
                orderMap.put("productTitle", product.getTitle());
                orderMap.put("productImage", OrderService.firstImage(product.getImages()));
            } else {
                orderMap.put("productTitle", "商品已删除");
                orderMap.put("productImage", "");
            }

            orderMap.put("price", order.getPrice() != null ? order.getPrice().toString() : "0");
            orderMap.put("status", order.getStatus());
            orderMap.put("createdAt", order.getCreatedTime() != null ? order.getCreatedTime().toString() : "");
            orderMap.put("sellerId", order.getSellerId());

            User seller = sellers.get(order.getSellerId());
            orderMap.put("sellerName", seller != null ? seller.getUsername() : "未知卖家");

            // 补齐买卖双方 ID：前端聊天页按 userId 开会话，缺了它无从发起
            orderMap.put("buyerId", order.getBuyerId());

            result.add(orderMap);
        }
        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getOrderById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        Long userId = userOpt.get().getId();
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return ResponseEntity.ok(ApiResponse.error(403, "无权访问此订单"));
        }
        // 惰性过期：超时未支付的订单在此自动取消并释放商品
        orderService.autoCancelIfExpired(order);
        // 返回收银台所需的全部交易要素
        return ResponseEntity.ok(ApiResponse.success("获取成功", orderService.buildCashierView(order)));
    }

    /** 创建订单（收银台入口） */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<?>> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Long> request) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        Long productId = request.get("productId");
        if (productId == null) {
            return ResponseEntity.ok(ApiResponse.error(400, "商品ID不能为空"));
        }
        try {
            Order order = orderService.createOrder(productId, userOpt.get().getId());
            // 返回订单 ID 与编号，前端据此跳转收银台
            return ResponseEntity.ok(ApiResponse.success("创建成功",
                    orderService.buildCashierView(order)));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 模拟支付。
     *
     * <p>请求体：{@code {"pay_method": "余额|支付宝|微信"}}。
     * 后端不做白名单校验，前端传什么就记什么，但不允许为空。</p>
     *
     * <p>支付渠道由 {@code PaymentService} 抽象，当前为本地 Mock 实现，
     * 不对接任何真实支付网关。</p>
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<?>> payOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        if (orderRepository.selectById(id) == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        String payMethod = body == null ? null : body.get("pay_method");

        // 归属校验、过期检查、状态守卫均在服务层完成；
        // 业务失败通过返回值表达而非抛异常，避免回滚掉「超时取消 + 商品释放」
        PayResult result = orderService.payOrder(id, payMethod, userOpt.get().getId());
        if (!result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.error(result.getCode(), result.getMessage()));
        }

        Order paid = orderRepository.selectById(id);
        Map<String, Object> data = new LinkedHashMap<>(orderService.buildCashierView(paid));
        data.put("tradeNo", result.getTradeNo());
        data.put("paymentChannel", orderService.getPaymentChannel());
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), data));
    }

/**
     * 提交评价：仅订单买家本人、仅已完成订单、一单一评。
     *
     * <p>权限与状态校验在 {@link ReviewService} 内完成，这里只负责取当前登录用户
     * 并把 {@link BadRequestException} 翻译成 400。</p>
     */
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<?>> submitReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        try {
            Integer rating = body == null || body.get("rating") == null
                    ? null
                    : Integer.valueOf(String.valueOf(body.get("rating")).trim());
            String content = body == null || body.get("content") == null
                    ? null
                    : String.valueOf(body.get("content"));

            Review review = reviewService.submit(id, userOpt.get().getId(), rating, content);
            return ResponseEntity.ok(ApiResponse.success("评价成功", toReviewView(review)));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("提交评价失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "评价失败，请稍后重试"));
        }
    }

    /**
     * 订单评价回显，供详情页显示已评价内容；未评价返回 data=null 而非 404。
     *
     * <p><b>可见性：仅订单双方可见，不是公开接口。</b> 评价内容里带着
     * reviewerId / targetId / 订单号，属于买卖双方的交易记录；顾客评价若要做成
     * 公开口碑，应另开一个按 productId 聚合、且只返回脱敏字段的端点，
     * 而不是把订单维度的时间线直接暴露给任何人。</p>
     */
    @GetMapping("/{id}/review")
    public ResponseEntity<ApiResponse<?>> getReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        // 先确认订单存在并校验归属，避免"猜 id 就能读到别人订单的评价"
        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        Long userId = userOpt.get().getId();
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            return ResponseEntity.ok(ApiResponse.error(403, "无权查看此订单的评价"));
        }
        try {
            Review review = reviewService.findByOrderId(id);
            return ResponseEntity.ok(ApiResponse.success("获取成功",
                    review == null ? null : toReviewView(review)));
        } catch (Exception e) {
            log.error("获取评价失败: ", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取失败"));
        }
    }

    private Map<String, Object> toReviewView(Review review) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", review.getId());
        view.put("orderId", review.getOrderId());
        view.put("productId", review.getProductId());
        view.put("reviewerId", review.getReviewerId());
        view.put("targetId", review.getTargetId());
        view.put("rating", review.getRating());
        view.put("content", review.getContent());
        view.put("createTime", review.getCreateTime());
        return view;
    }

    /**
     * 批量支付：一次付清购物车结算出的全部订单。
     *
     * <p>Body: {@code { orderIds: "1,2,3", payMethod: "alipay" }}，
     * 与前端 {@code api/order.js#batchPayOrders} 的契约一致。</p>
     *
     * <p><b>循环刻意写在 Controller 里而不是 OrderService 内部</b>：
     * {@code payOrder} 带 {@code @Transactional}，若在同一个 Service 内
     * 自调用（this.payOrder），事务不会通过代理生效，多笔支付会被并进一个事务，
     * 任意一笔失败会导致已成功的支付一起回滚。这里经注入的 orderService 逐笔调用，
     * 每笔各自开启并提交事务，因此部分失败不影响其余。</p>
     *
     * <p>{@code payOrder} 的业务失败是「返回 fail 而非抛异常」，故单笔失败
     * 不会触发回滚；这里仍逐笔 catch，保证异常路径也不会中断整批。</p>
     */
    @PostMapping("/batch-pay")
    public ResponseEntity<ApiResponse<?>> batchPay(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "请先登录"));
        }
        Long userId = userOpt.get().getId();

        String payMethod = body.get("payMethod") == null ? "alipay" : String.valueOf(body.get("payMethod"));
        List<Long> orderIds = parseOrderIds(body.get("orderIds"));
        if (orderIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "请选择要支付的订单"));
        }

        List<Long> successIds = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String tradeNo = null;

        for (Long orderId : orderIds) {
            try {
                PayResult result = orderService.payOrder(orderId, payMethod, userId);
                if (result.isSuccess()) {
                    successIds.add(orderId);
                    if (tradeNo == null) {
                        tradeNo = result.getTradeNo();
                    }
                    Order paid = orderRepository.selectById(orderId);
                    if (paid != null) {
                        BigDecimal price = paid.getPrice() == null ? BigDecimal.ZERO : paid.getPrice();
                        BigDecimal shipping = paid.getShippingFee() == null ? BigDecimal.ZERO : paid.getShippingFee();
                        totalAmount = totalAmount.add(price).add(shipping);
                    }
                } else {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("orderId", orderId);
                    row.put("reason", result.getMessage());
                    failed.add(row);
                }
            } catch (Exception e) {
                log.error("批量支付订单 {} 失败: ", orderId, e);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderId", orderId);
                row.put("reason", "该订单支付失败");
                failed.add(row);
            }
        }

        if (successIds.isEmpty()) {
            // 一笔都没成功：整体失败，前端据此展示错误而非"支付成功"
            String reason = failed.isEmpty() ? "支付失败" : String.valueOf(failed.get(0).get("reason"));
            return ResponseEntity.ok(ApiResponse.error(400, reason));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("successIds", successIds);
        data.put("failedIds", failed.stream().map(f -> f.get("orderId")).toList());
        data.put("failed", failed);
        data.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP));
        data.put("tradeNo", tradeNo);
        data.put("paymentChannel", orderService.getPaymentChannel());
        return ResponseEntity.ok(ApiResponse.success(
                failed.isEmpty() ? "支付成功" : "部分订单支付成功", data));
    }

    /** orderIds 兼容数组 [1,2] 与逗号分隔字符串 "1,2" 两种形态 */
    private List<Long> parseOrderIds(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Long id = toLongOrNull(item);
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        }
        if (raw != null) {
            for (String part : String.valueOf(raw).split(",")) {
                Long id = toLongOrNull(part);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private Long toLongOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取消订单：商品恢复为在售 */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancelOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        Long userId = userOpt.get().getId();
        // 买家可取消自己的待支付订单；卖家不能单方面取消（应走下架流程）
        if (!order.getBuyerId().equals(userId)) {
            return ResponseEntity.ok(ApiResponse.error(403, "只有买家可以取消该订单"));
        }
        try {
            Order cancelled = orderService.cancelOrder(id);
            return ResponseEntity.ok(ApiResponse.success("订单已取消，商品已恢复在售",
                    orderService.buildCashierView(cancelled)));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 发货：仅卖家、仅「待发货」订单。
     *
     * <p><b>这里替换掉了原先通用的 {@code PUT /api/orders/{id}}。</b>
     * 那个端点只校验「调用者是买卖双方之一」，随后把请求体里的 status 原样写库，
     * 于是：买家能把刚创建的待支付订单直接改成「待收货」白拿商品；卖家能替买家
     * 点「确认收货」；已取消的订单也能被改回「已完成」。现在拆成两个语义明确的
     * 端点，每个都绑定「特定角色 + 特定前置状态」。</p>
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<?>> shipOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        try {
            Order shipped = orderService.shipOrder(id, userOpt.get().getId());
            return ResponseEntity.ok(ApiResponse.success("已发货",
                    orderService.buildCashierView(shipped)));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 确认收货：仅买家、仅「待收货」订单。
     *
     * <p>状态用 {@code WHERE id=? AND status=待收货} 的条件 UPDATE 推进，
     * 影响行数为 0 即返回失败——避免并发下「取消」与「确认收货」互相覆盖。</p>
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<?>> confirmReceipt(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        try {
            Order completed = orderService.confirmReceipt(id, userOpt.get().getId());
            return ResponseEntity.ok(ApiResponse.success("确认收货成功",
                    orderService.buildCashierView(completed)));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Optional<User> userOpt = getUserFromToken(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
        }
        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        Long userId = userOpt.get().getId();
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return ResponseEntity.ok(ApiResponse.error(403, "无权操作此订单"));
        }
        // 硬删除不可逆：进行中的订单（待支付/待发货/待收货）删掉就等于抹掉交易凭证，
        // 商品已锁定、钱已收，却再也查不到这笔单子。只允许清理已终结的订单。
        if (!OrderStatus.isCompleted(order.getStatus()) && !OrderStatus.isCancelled(order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "仅已完成或已取消的订单可以删除"));
        }
        orderRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }
}