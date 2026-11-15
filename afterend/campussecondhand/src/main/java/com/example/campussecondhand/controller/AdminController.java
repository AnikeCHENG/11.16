package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.campussecondhand.service.AdminStatsService;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.common.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.enums.ProductReviewStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Message;
import com.example.campussecondhand.entity.Category;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.service.PenaltyService;
import com.example.campussecondhand.service.OrderService;
import com.example.campussecondhand.service.ProductReviewService;
import com.example.campussecondhand.entity.Report;
import com.example.campussecondhand.entity.StudentVerification;
import com.example.campussecondhand.service.ReportService;
import com.example.campussecondhand.service.StudentVerificationService;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.MessageRepository;
import com.example.campussecondhand.repository.CategoryRepository;
import com.example.campussecondhand.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
/**
 * 平台管理后台。
 *
 * <p>鉴权已上移至 {@code AdminAuthInterceptor}：所有 {@code /api/admin/**}
 * 在进入本类之前就会校验 JWT 中的 role，非管理员直接返回 403。本类不再重复判断，
 * 避免新增端点时漏写检查造成越权。</p>
 */
public class AdminController {

    /**
     * 批量接口单次可处理的最大条目数。
     *
     * <p>批量接口没有上限时，一个长度 10 万的数组就是 20 万次数据库往返——
     * 任何登录的管理员（或被盗的管理员账号）都能用它把库拖垮。</p>
     */
    private static final int BATCH_UPDATE_LIMIT = 100;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PenaltyService penaltyService;

    @Autowired
    private ProductReviewService productReviewService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private StudentVerificationService studentVerificationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminStatsService adminStatsService;

    // ==================== 用户管理 ====================

// 获取所有用户
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<?>> getAllUsers(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // 分页参数统一校验：page<1 或 size 越界直接 400，不再静默纠正
        PageParam paging = PageParam.of(page, size);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (search != null && !search.isEmpty()) {
            // OR 必须包进 and(...)：否则后续追加的条件会被 OR 拆开
            wrapper.and(q -> q.like("username", search).or().like("email", search));
        }
        wrapper.orderByDesc("created_time", "id");

        // 物理分页：由分页拦截器拼 LIMIT，不是查出全量再在内存里 skip/limit
        IPage<User> paged = userRepository.selectPage(paging.toPage(), wrapper);

        return ResponseEntity.ok(ApiResponse.success("获取成功",
                PageResult.of(paged.getRecords(), paged.getTotal(), paging)));
    }

    // 获取用户详情
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<?>> getUserDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
User user = userRepository.selectById(id);
        if (user == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "用户不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success("获取成功", user));
    }

// 禁用/启用用户（处罚逻辑已收敛到 PenaltyService，此处仅做转发）
    @PutMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<?>> updateUserStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Integer status) {
        // 白名单：users.status 只有 1 正常 / 0 禁用 两种语义。
        // 此前接受任意 Integer（例如 7），会写进一个既非正常也非封禁、
        // 任何筛选都看不到的状态值。
        if (status == null || (status != 0 && status != 1)) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "非法的用户状态值，只允许 0（禁用）或 1（正常）"));
        }

        // 自我封锁：全站只有管理员能进后台，把自己封了就没有第二个账号能恢复
        Long currentUserId = currentUserId(authHeader);
        if (currentUserId != null && currentUserId.equals(id)) {
            return ResponseEntity.ok(ApiResponse.error(400, "不能修改自己的账号状态"));
        }

        User target = userRepository.selectById(id);
        if (target == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "用户不存在"));
        }
        // 与举报处理路径同一口径（ReportService「不能封禁管理员账号」）：
        // 否则一次误操作就能锁死管理员，且没有旁人能救回来
        if (target.getRole() != null && target.getRole() == 1) {
            return ResponseEntity.ok(ApiResponse.error(403, "不能修改管理员的账号状态"));
        }

        try {
            User user = penaltyService.updateUserStatus(id, status, operatorOf(authHeader));
            return ResponseEntity.ok(ApiResponse.success("更新成功", user));
        } catch (BadRequestException e) {
            // 保持与重构前一致的 HTTP 200 + code=404/400 语义
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        }
    }

    /** 从请求头解析操作者昵称，仅用于日志；解析失败不影响业务 */
    private String operatorOf(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "unknown";
        }
        String username = jwtUtil.getUsernameFromToken(authHeader.substring(7));
        return username == null ? "unknown" : username;
    }

    /**
     * 从请求头解析当前登录管理员的主键。
     *
     * <p>拦截器已保证调用者是有效管理员，这里只是为了让"不能封自己"这条规则
     * 拿到操作者身份；解析失败返回 {@code null}（该规则不生效，但其余校验照旧）。</p>
     */
    private Long currentUserId(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null;
            }
            String username = jwtUtil.getUsernameFromToken(authHeader.substring(7));
            if (username == null) {
                return null;
            }
            return userRepository.findByUsername(username).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 商品管理 ====================

    // 获取所有商品（支持搜索、筛选、分页）
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<?>> getAllProducts(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer reviewStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // 分页参数统一校验：page<1 或 size 越界直接 400，不再静默纠正
        PageParam paging = PageParam.of(page, size);

com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Product> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (search != null && !search.isEmpty()) {
            wrapper.and(q -> q.like("title", search).or().like("description", search));
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (reviewStatus != null) {
            wrapper.eq("review_status", reviewStatus);
        }
        wrapper.orderByDesc("created_time", "id");

        // 物理分页：由分页拦截器拼 LIMIT
        IPage<Product> paged = productRepository.selectPage(paging.toPage(), wrapper);

        return ResponseEntity.ok(ApiResponse.success("获取成功",
                PageResult.of(paged.getRecords(), paged.getTotal(), paging)));
    }

    // 获取商品详情
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<?>> getProductDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
Product product = productRepository.selectById(id);
        if (product == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success("获取成功", product));
    }

// 更新商品状态（处罚逻辑已收敛到 PenaltyService，此处仅做转发）
    @PutMapping("/products/{id}/status")
    public ResponseEntity<ApiResponse<?>> updateProductStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Integer status) {
        try {
            Product product = penaltyService.updateProductStatus(id, status, operatorOf(authHeader));
            return ResponseEntity.ok(ApiResponse.success("更新成功", product));
        } catch (BadRequestException e) {
            // 保持与重构前一致的 HTTP 200 + code=400 语义
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 审核单件商品：{@code approve} 通过上架 / {@code reject} 拒绝并要求填写理由。
     *
     * <p>审核通过会同时把 {@code review_status} 置为已通过、{@code status} 置为在售；
     * 拒绝则保持下架并记录理由，卖家可修改后重新提交。</p>
     */
    @PutMapping("/products/{id}/review")
    public ResponseEntity<ApiResponse<?>> reviewProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {
        try {
            Object rawAction = params.get("action");
            String action = rawAction == null ? null : String.valueOf(rawAction);
            Object rawReason = params.get("reason");
            String reason = rawReason == null ? null : String.valueOf(rawReason);
            Product product = productReviewService.review(id, action, reason, currentUserId(authHeader));
            boolean rejected = ProductReviewStatus.fromCode(product.getReviewStatus())
                    == ProductReviewStatus.REJECTED;
            return ResponseEntity.ok(ApiResponse.success(rejected ? "已拒绝" : "审核通过", product));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 批量通过商品审核。
     *
     * <p>只提供批量「通过」：拒绝必须逐条填写理由，批量拒绝无法给出有意义的原因，
     * 卖家也就无从修改。单次上限与其它批量接口一致。</p>
     */
    @PutMapping("/products/batch/review")
    public ResponseEntity<ApiResponse<?>> batchApproveProducts(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> params) {
        Object rawIds = params.get("ids");
        if (!(rawIds instanceof List<?> idList) || idList.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "ids 必须是数组且不能为空"));
        }
        if (idList.size() > BATCH_UPDATE_LIMIT) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "一次最多批量审核 " + BATCH_UPDATE_LIMIT + " 件商品"));
        }
        List<Long> ids = new ArrayList<>();
        for (Object rawId : idList) {
            try {
                ids.add(Long.valueOf(String.valueOf(rawId).trim()));
            } catch (NumberFormatException e) {
                return ResponseEntity.ok(ApiResponse.error(400, "商品 id 必须是数字"));
            }
        }
        Map<String, Integer> result = productReviewService.batchApprove(ids, currentUserId(authHeader));
        return ResponseEntity.ok(ApiResponse.success("批量审核完成", result));
    }

    // 批量更新商品状态
    @PutMapping("/products/batch/status")
    public ResponseEntity<ApiResponse<?>> batchUpdateProductStatus(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> params) {
        // 类型安全：此前直接 (List<?>) 强转，前端传 "1,2,3" 这种字符串会
        // ClassCastException → 500，日志里只看得到"服务器内部错误"
        Object rawIds = params.get("ids");
        if (!(rawIds instanceof List<?> idList) || idList.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "ids 必须是数组且不能为空"));
        }
        // 上限：一次最多改 100 件。没有上限时，一个 10 万长度的数组
        // 就是 20 万次数据库往返，任何登录的管理员都能把库拖垮
        if (idList.size() > BATCH_UPDATE_LIMIT) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "一次最多批量修改 " + BATCH_UPDATE_LIMIT + " 件商品"));
        }

        Object rawStatus = params.get("status");
        if (rawStatus == null) {
            return ResponseEntity.ok(ApiResponse.error(400, "状态不能为空"));
        }
        Integer status;
        try {
            status = Integer.valueOf(rawStatus.toString().trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.ok(ApiResponse.error(400, "状态值必须是数字"));
        }

        // 白名单校验：只接受 ProductStatus 中存在的取值
        ProductStatus target = ProductStatus.fromCode(status);
        if (target == null) {
            return ResponseEntity.ok(ApiResponse.error(400, "非法的商品状态值"));
        }

        int skippedCount = 0;
        int updatedCount = 0;
        for (Object rawId : idList) {
            Long id;
            try {
                id = Long.valueOf(String.valueOf(rawId).trim());
            } catch (NumberFormatException e) {
                // 单个 id 非法只跳过它：批量操作里混进一个脏值，
                // 不该让其余 99 件已选中的商品白改一遍
                skippedCount++;
                continue;
            }
            Product product = productRepository.selectById(id);
            if (product != null) {
                // 与单件上架同一门禁：未通过审核的商品不能借批量接口绕过审核
                if (target == ProductStatus.ON_SALE
                        && !ProductReviewStatus.isApproved(product.getReviewStatus())) {
                    skippedCount++;
                    continue;
                }
                product.setStatus(target.getCode());
                productRepository.updateById(product);
                updatedCount++;
            } else {
                skippedCount++;
            }
        }

        return ResponseEntity.ok(ApiResponse.success("批量更新成功",
                Map.of("updatedCount", updatedCount, "skippedCount", skippedCount)));
    }

    // ==================== 订单管理 ====================

    // 获取所有订单（支持按订单号 / 商品标题搜索）
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<?>> getAllOrders(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // 分页参数统一校验：page<1 或 size 越界直接 400，不再静默纠正
        PageParam paging = PageParam.of(page, size);

        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        // 管理端订单搜索：前端一直有个搜索框，但后端此前没有这个参数，
        // Spring 静默忽略未知查询参数，于是"搜索"按钮其实什么都没做
        if (search != null && !search.isBlank()) {
            String keyword = search.trim();
            wrapper.and(q -> q.like("order_no", keyword).or().like("order_title", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_time", "id");

        // 物理分页：由分页拦截器拼 LIMIT
        IPage<Order> paged = orderRepository.selectPage(paging.toPage(), wrapper);

        return ResponseEntity.ok(ApiResponse.success("获取成功",
                PageResult.of(toAdminOrderViews(paged.getRecords()), paged.getTotal(), paging)));
    }

    // 获取订单详情
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<?>> getOrderDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        // 详情与列表用同一套视图，避免"列表有昵称、点开就变未知用户"
        return ResponseEntity.ok(ApiResponse.success("获取成功",
                toAdminOrderViews(List.of(order)).get(0)));
    }

    /**
     * 管理端订单视图：补上买家/卖家昵称与商品标题。
     *
     * <p><b>为什么必须在这里补：</b>{@code Order} 的 buyer / seller / product 三个字段
     * 都标了 {@code @TableField(exist = false)}，不参与序列化，裸实体里只有
     * buyerId / sellerId / productId。前端管理页读的是 {@code order.user?.username}、
     * {@code order.product?.name}、{@code order.createdAt}——三个字段全不存在，
     * 于是每张订单卡片都显示"未知用户 / 未知商品 / ¥0 / 空白时间"。</p>
     *
     * <p>买家与卖家按当页 id 批量查询，避免每条订单两次 selectById 的 N+1。</p>
     */
    private List<Map<String, Object>> toAdminOrderViews(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        for (Order o : orders) {
            if (o.getBuyerId() != null) {
                userIds.add(o.getBuyerId());
            }
            if (o.getSellerId() != null) {
                userIds.add(o.getSellerId());
            }
            if (o.getProductId() != null) {
                productIds.add(o.getProductId());
            }
        }
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userRepository.selectBatchIds(userIds.stream().distinct().toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productRepository.selectBatchIds(productIds.stream().distinct().toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (Order o : orders) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", o.getId());
            view.put("orderNo", o.getOrderNo());
            view.put("status", o.getStatus());
            view.put("buyerId", o.getBuyerId());
            view.put("sellerId", o.getSellerId());
            view.put("productId", o.getProductId());
            view.put("price", o.getPrice());
            view.put("shippingFee", o.getShippingFee());
            view.put("serviceFee", o.getServiceFee());
            view.put("sellerIncome", o.getSellerIncome());
            view.put("createdTime", o.getCreatedTime());
            view.put("paidTime", o.getPaidTime());
            view.put("shippedTime", o.getShippedTime());
            view.put("completedTime", o.getCompletedTime());
            view.put("refundFromStatus", o.getRefundFromStatus());
            view.put("refundReason", o.getRefundReason());
            view.put("refundReply", o.getRefundReply());
            view.put("refundApplyTime", o.getRefundApplyTime());
            view.put("refundTime", o.getRefundTime());
            view.put("refundAmount", o.getRefundAmount());

            User buyer = users.get(o.getBuyerId());
            User seller = users.get(o.getSellerId());
            view.put("buyerName", buyer != null ? buyer.getUsername() : "未知用户");
            view.put("sellerName", seller != null ? seller.getUsername() : "未知卖家");

            // 标题优先取订单快照：商品被删改后，成交时的信息仍然可查
            Product product = products.get(o.getProductId());
            view.put("productTitle", o.getOrderTitle() != null
                    ? o.getOrderTitle()
                    : (product != null ? product.getTitle() : "商品已删除"));

            result.add(view);
        }
        return result;
    }

    // 更新订单状态（管理端应急处置）
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<?>> updateOrderStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
        String statusStr = params.get("status");
        if (statusStr == null || statusStr.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "状态不能为空"));
        }

        // 白名单：此前把任意 Integer 直接写进 orders.status。
        // 传 30 会存进一个任何筛选都看不到的状态；传 3 则要看它从哪来（见下）
        Integer status;
        try {
            status = Integer.parseInt(statusStr.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.ok(ApiResponse.error(400, "状态值必须是数字"));
        }
        OrderStatus target = OrderStatus.fromCode(status);
        if (target == null) {
            return ResponseEntity.ok(ApiResponse.error(400, "非法的订单状态值"));
        }

        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }

        // 终态只允许保持原状，不允许被改写回中间状态（订单是对账凭证）
        if (OrderStatus.isTerminal(order.getStatus())
                && !java.util.Objects.equals(target.getCode(), order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.error(400, "已完成/已取消/已退款的订单不能再改状态"));
        }

        if (java.util.Objects.equals(target.getCode(), order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success("状态未变化",
                    toAdminOrderViews(List.of(order)).get(0)));
        }

        // 状态推进必须走领域服务：只改 status 会留下「商品还锁着」「卖家没收到钱」
        // 这类账实不符的中间态。这里只保留两个管理端确有需要的应急处置。
        try {
            if (target == OrderStatus.CANCELLED) {
                if (!OrderStatus.isPendingPayment(order.getStatus())) {
                    return ResponseEntity.ok(ApiResponse.error(400,
                            "已支付订单不能直接取消，请使用退款接口"));
                }
                Order cancelled = orderService.adminCancelUnpaid(id);
                return ResponseEntity.ok(ApiResponse.success("订单已取消，商品已恢复在售",
                        toAdminOrderViews(List.of(cancelled)).get(0)));
            }
            if (target == OrderStatus.COMPLETED) {
                if (OrderStatus.isPendingPayment(order.getStatus())) {
                    return ResponseEntity.ok(ApiResponse.error(400,
                            "待支付订单不能直接标记为已完成"));
                }
                if (OrderStatus.fromCode(order.getStatus()) != OrderStatus.PENDING_RECEIPT) {
                    return ResponseEntity.ok(ApiResponse.error(400,
                            "仅待收货订单可以标记为已完成"));
                }
                Order completed = orderService.adminComplete(id);
                return ResponseEntity.ok(ApiResponse.success("订单已完成，卖家已入账",
                        toAdminOrderViews(List.of(completed)).get(0)));
            }
            return ResponseEntity.ok(ApiResponse.error(400,
                    "该状态不支持手动修改；退款请使用退款接口"));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 管理员退款：已支付未确认收货(1/2) 直接退，或同意买家已提交的退款申请(5)。
     *
     * <p>与用户端卖家同意退款共用 {@link OrderService#adminRefund}，
     * 商品恢复在售与状态推进在同一事务内完成。</p>
     */
    @PostMapping("/orders/{id}/refund")
    public ResponseEntity<ApiResponse<?>> refundOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body == null ? null : body.get("reason");
            String reply = body == null ? null : body.get("reply");
            Order refunded = orderService.adminRefund(id, reason, reply);
            return ResponseEntity.ok(ApiResponse.success("退款成功，商品已恢复在售",
                    toAdminOrderViews(List.of(refunded)).get(0)));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** 管理员驳回退款申请：5 → 申请前状态 */
    @PostMapping("/orders/{id}/refund/reject")
    public ResponseEntity<ApiResponse<?>> rejectRefund(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reply = body == null ? null : body.get("reply");
            Order back = orderService.adminRejectRefund(id, reply);
            return ResponseEntity.ok(ApiResponse.success("已驳回退款申请",
                    toAdminOrderViews(List.of(back)).get(0)));
        } catch (OrderService.OrderBusinessException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    // 删除订单
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<?>> deleteOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Order order = orderRepository.selectById(id);
        if (order == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "订单不存在"));
        }
        // 与用户端 DELETE /api/orders/{id} 同一口径：orderRepository 是物理删除
        // （orders 实体没有逻辑删除字段），删掉一笔进行中的订单等于抹掉交易凭证，
        // 商品已锁定、钱已收，却再也查不到这笔单子，财务统计也被改写
        if (!OrderStatus.isTerminal(order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "仅已完成、已取消或已退款的订单可以删除"));
        }
        // 已有资金流水的订单同样不能删：account_flow / payment_record 通过
        // order_id 关联订单且无级联，删掉订单只会留下「有流水无订单」的孤儿记录
        if (!orderService.canDelete(order)) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "该订单已有资金流水，不能删除"));
        }

        orderRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    // ==================== 消息管理 ====================

    // 获取所有消息
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<?>> getAllMessages(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // 分页参数统一校验：page<1 或 size 越界直接 400，不再静默纠正
        PageParam paging = PageParam.of(page, size);

com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Message> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (search != null && !search.isEmpty()) {
            wrapper.like("content", search);
        }
        // 排序与分页都交给 SQL：原来先查全量再在内存里 filter/sorted/skip，
        // 既是假分页，也会把 created_time 为 null 的行悄悄滤掉导致 total 对不上
        wrapper.isNotNull("created_time")
                .orderByDesc("created_time", "id");

        // 物理分页：由分页拦截器拼 LIMIT
        IPage<Message> paged = messageRepository.selectPage(paging.toPage(), wrapper);

        return ResponseEntity.ok(ApiResponse.success("获取成功",
                PageResult.of(paged.getRecords(), paged.getTotal(), paging)));
    }

    // 删除消息
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<ApiResponse<?>> deleteMessage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
Message message = messageRepository.selectById(id);
        if (message == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "消息不存在"));
        }

        messageRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    // ==================== 分类管理 ====================

    /** 分类标识（商品表存的就是这个 slug）：小写字母开头，允许数字/下划线/连字符，2~50 字符 */
    private static final java.util.regex.Pattern CATEGORY_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_-]{1,49}$");

    // 获取所有分类（含真实商品数与启停状态）
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<?>> getAllCategories(
            @RequestHeader("Authorization") String authHeader) {
        List<Category> categories = categoryRepository.selectList(
                new QueryWrapper<Category>().orderByAsc("sort_order", "id"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Category category : categories) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.getId());
            row.put("code", category.getCode());
            row.put("name", category.getName());
            row.put("description", category.getDescription());
            row.put("icon", category.getIcon());
            row.put("parentId", category.getParentId());
            row.put("sortOrder", category.getSortOrder());
            row.put("status", category.getStatus());
            // 商品表存的是 code（slug），旧实现按中文 name 统计，因此永远显示 0。
            // 这是「后台分类管理看起来没起作用」的根因之一。
            long productCount = category.getCode() == null ? 0
                    : productRepository.selectCount(new QueryWrapper<Product>()
                            .eq("category", category.getCode()));
            row.put("productCount", productCount);
            result.add(row);
        }

        return ResponseEntity.ok(ApiResponse.success("获取成功", result));
    }

    // 添加分类
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<?>> addCategory(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> params) {
        String name = text(params.get("name"));
        if (name.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "分类名称不能为空"));
        }
        String code = text(params.get("code")).toLowerCase();
        if (!CATEGORY_CODE_PATTERN.matcher(code).matches()) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "分类标识须以小写字母开头，只能包含字母/数字/下划线/连字符，长度 2~50"));
        }
        if (categoryRepository.selectCount(new QueryWrapper<Category>().eq("code", code)) > 0) {
            return ResponseEntity.ok(ApiResponse.error(400, "分类标识已存在"));
        }

        Category category = new Category();
        category.setName(name);
        category.setCode(code);
        category.setDescription(textOrNull(params.get("description")));
        category.setSortOrder(intOr(params.get("sortOrder"), 0));
        category.setStatus(intOr(params.get("status"), 1));
        categoryRepository.insert(category);

        return ResponseEntity.ok(ApiResponse.success("添加成功", category));
    }

    // 更新分类：code 不可修改，商品按它归属；改名不影响已有商品
    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<?>> updateCategory(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {
        Category category = categoryRepository.selectById(id);
        if (category == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "分类不存在"));
        }

        String code = text(params.get("code"));
        if (!code.isEmpty() && !code.equalsIgnoreCase(category.getCode())) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "分类标识不可修改；如需更换标识，请新建分类并迁移商品"));
        }
        if (params.containsKey("name")) {
            String name = text(params.get("name"));
            if (name.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(400, "分类名称不能为空"));
            }
            category.setName(name);
        }
        if (params.containsKey("description")) {
            category.setDescription(textOrNull(params.get("description")));
        }
        if (params.containsKey("sortOrder")) {
            category.setSortOrder(intOr(params.get("sortOrder"), category.getSortOrder()));
        }
        if (params.containsKey("status")) {
            Integer status = intOr(params.get("status"), category.getStatus());
            if (status == null || (status != 0 && status != 1)) {
                return ResponseEntity.ok(ApiResponse.error(400, "分类状态只能是 0（停用）或 1（启用）"));
            }
            category.setStatus(status);
        }
        categoryRepository.updateById(category);

        return ResponseEntity.ok(ApiResponse.success("更新成功", category));
    }

    // 删除分类：仍有商品使用该分类时拒绝删除，避免商品变孤儿
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<?>> deleteCategory(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Category category = categoryRepository.selectById(id);
        if (category == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "分类不存在"));
        }

        long inUse = category.getCode() == null ? 0
                : productRepository.selectCount(new QueryWrapper<Product>()
                        .eq("category", category.getCode()));
        if (inUse > 0) {
            return ResponseEntity.ok(ApiResponse.error(400,
                    "该分类下还有 " + inUse + " 件商品，请先迁移或停用分类"));
        }

        categoryRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    /** 参数转字符串并 trim；null 视为空串 */
    private static String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    /** 空串归一为 null，避免数据库里同时存在 NULL 与 "" 两种空值 */
    private static String textOrNull(Object raw) {
        String value = text(raw);
        return value.isEmpty() ? null : value;
    }

    /** 宽松整数解析：缺省或非法时用兜底值 */
    private static Integer intOr(Object raw, Integer fallback) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return fallback;
        }
        try {
            return Integer.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

// ==================== 数据统计 ====================

    /**
     * 平台概览。
     *
     * <p>原先用 {@code selectList(null).size()} 取计数——会把整表实体加载进内存
     * 只为得到一个数字，数据量一大就是明显的性能反模式。改用 {@code selectCount}，
     * 由数据库直接返回 COUNT。</p>
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<?>> getStats(
            @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userRepository.selectCount(null));
        stats.put("productCount", productRepository.selectCount(null));
        stats.put("orderCount", orderRepository.selectCount(null));
        stats.put("messageCount", messageRepository.selectCount(null));
        // 在售与已售出分开计数，便于管理员判断平台供给结构
        QueryWrapper<Product> onSale = new QueryWrapper<>();
        onSale.eq("status", ProductStatus.ON_SALE.getCode());
        stats.put("onSaleProductCount", productRepository.selectCount(onSale));
        QueryWrapper<Product> sold = new QueryWrapper<>();
        sold.eq("status", ProductStatus.SOLD.getCode());
        stats.put("soldProductCount", productRepository.selectCount(sold));
        // 待审核数量：管理后台菜单/首页红点用，避免管理员漏审
        QueryWrapper<Product> pendingReview = new QueryWrapper<>();
        pendingReview.eq("review_status", ProductReviewStatus.PENDING.getCode());
        stats.put("pendingReviewProductCount", productRepository.selectCount(pendingReview));

        return ResponseEntity.ok(ApiResponse.success("获取成功", stats));
    }

    /**
     * 财务统计：累计成交额、平台手续费总额、卖家应收合计、近 6 个月趋势。
     *
     * <p>手续费口径见 {@link com.example.campussecondhand.service.AdminStatsService}：
     * 仅统计已支付且未取消的订单。</p>
     */
    @GetMapping("/stats/finance")
    public ResponseEntity<ApiResponse<?>> getFinance(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(ApiResponse.success("获取成功", adminStatsService.finance()));
    }

    /** 近 N 天每日新增用户/商品/订单趋势，默认 30 天 */
    @GetMapping("/stats/trend")
    public ResponseEntity<ApiResponse<?>> getTrend(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "30") Integer days) {
        int d = (days == null || days < 1) ? 30 : Math.min(days, 365);
        return ResponseEntity.ok(ApiResponse.success("获取成功", adminStatsService.dailyTrend(d)));
    }

    /** 商品分类交易热度（饼图数据源） */
    @GetMapping("/stats/category")
    public ResponseEntity<ApiResponse<?>> getCategoryHeat(
            @RequestHeader("Authorization") String authHeader) {
return ResponseEntity.ok(ApiResponse.success("获取成功", adminStatsService.categoryHeat()));
    }


    // ==================== 举报处理 ====================

    /**
     * 举报列表（物理分页）。支持按处理状态、对象类型筛选，以及按举报原因做关键词搜索。
     */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<?>> getReports(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        try {
            PageParam paging = PageParam.of(page, size);
            PageResult<Map<String, Object>> result =
                    reportService.pageReports(paging, status, targetType, search);
            return ResponseEntity.ok(ApiResponse.success("获取成功", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 处理举报：执行处罚并更新举报状态，全程单事务。
     *
     * <p>动作与被举报对象类型的匹配校验在服务端强制执行：
     * 商品只能 DELETE_PRODUCT，用户只能 BAN_USER，REJECT 两者皆可。
     * 前端虽按类型过滤了下拉选项，但那是 UX 层，直接调接口可以绕过。</p>
     */
    @PutMapping("/reports/{id}/handle")
    public ResponseEntity<ApiResponse<?>> handleReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
            String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
            Report report = reportService.handle(id, action, remark, operatorOf(authHeader));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", report.getId());
            result.put("status", report.getStatus());
            result.put("adminRemark", report.getAdminRemark());
            return ResponseEntity.ok(ApiResponse.success("处理完成", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 学生认证审核（支付宝截图辅助认证） ====================

    /**
     * 学生认证申请列表（支付宝截图辅助认证），物理分页。
     *
     * <p>列表按合规要求脱敏（姓名只留姓氏、学号打码）；完整信息走
     * {@link #getStudentVerificationDetail}。本路径在 /api/admin/** 下，
     * 鉴权由 AdminAuthInterceptor 保证。</p>
     */
    @GetMapping("/student-verifications")
    public ResponseEntity<ApiResponse<?>> getStudentVerifications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        try {
            PageParam paging = PageParam.of(page, size);
            PageResult<Map<String, Object>> result =
                    studentVerificationService.pageForAdmin(paging, status, search);
            return ResponseEntity.ok(ApiResponse.success("获取成功", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 申请详情：完整姓名与学号（管理员比对截图用）。
     */
    @GetMapping("/student-verifications/{id}")
    public ResponseEntity<ApiResponse<?>> getStudentVerificationDetail(
            @PathVariable Long id) {
        try {
            Map<String, Object> detail = studentVerificationService.detailForAdmin(id);
            return ResponseEntity.ok(ApiResponse.success("获取成功", detail));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 读取申请的认证截图。
     *
     * <p>截图存放在私有目录，没有对外 URL，只能经本接口按申请记录取；
     * 路径在 /api/admin/** 下，由 {@code AdminAuthInterceptor} 校验管理员身份。</p>
     */
    @GetMapping("/student-verifications/{id}/proof")
    public ResponseEntity<byte[]> getStudentVerificationProof(@PathVariable Long id) {
        try {
            StudentVerificationService.ProofFile proof = studentVerificationService.proof(id);
            MediaType mediaType = proof.fileName() != null && proof.fileName().endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(proof.bytes());
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 审核学生认证截图申请。action: approve / reject；
     * reject 时 reason（拒绝理由）必填，理由会展示给申请人并允许其重新提交。
     *
     * <p>通过后用户获得学生认证权限（is_student_verified = 1），与既有
     * 学号认证产物同构；若用户已有既有认证则保持原状不降级。</p>
     */
    @PutMapping("/student-verifications/{id}/review")
    public ResponseEntity<ApiResponse<?>> reviewStudentVerification(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
            String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
            StudentVerification record = studentVerificationService.review(
                    id, action, reason, currentUserId(authHeader), operatorOf(authHeader));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", record.getId());
            result.put("status", record.getStatus());
            result.put("reviewedAt", record.getReviewedAt());
            return ResponseEntity.ok(ApiResponse.success("审核完成", result));
        } catch (BadRequestException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }
}
