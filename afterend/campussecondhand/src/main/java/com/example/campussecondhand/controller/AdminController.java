package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.campussecondhand.service.AdminStatsService;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.common.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Message;
import com.example.campussecondhand.entity.Category;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.service.PenaltyService;
import com.example.campussecondhand.entity.Report;
import com.example.campussecondhand.service.ReportService;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.MessageRepository;
import com.example.campussecondhand.repository.CategoryRepository;
import com.example.campussecondhand.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private ReportService reportService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

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

        // 待支付订单不能直接标记为已完成：财务口径把 待发货/待收货/已完成 都算作
        // 已收款（见 AdminStatsService），越过支付直接完成等于凭空造出一笔 GMV，
        // 而 paid_time 仍为空又会让"月度服务费趋势"不认它，两个财务数字互相打架
        if (OrderStatus.isPendingPayment(order.getStatus()) && target == OrderStatus.COMPLETED) {
            return ResponseEntity.ok(ApiResponse.error(400, "待支付订单不能直接标记为已完成"));
        }
        // 终态只允许保持原状，不允许被改写回中间状态（订单是对账凭证）
        boolean terminal = OrderStatus.isCompleted(order.getStatus())
                || OrderStatus.isCancelled(order.getStatus());
        if (terminal && !java.util.Objects.equals(target.getCode(), order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.error(400, "已完成/已取消的订单不能再改状态"));
        }

        order.setStatus(target.getCode());
        if (target == OrderStatus.COMPLETED && order.getCompletedTime() == null) {
            order.setCompletedTime(java.time.LocalDateTime.now());
        }
        order.setUpdatedTime(java.time.LocalDateTime.now());
        orderRepository.updateById(order);

        return ResponseEntity.ok(ApiResponse.success("更新成功",
                toAdminOrderViews(List.of(order)).get(0)));
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
        if (!OrderStatus.isCompleted(order.getStatus())
                && !OrderStatus.isCancelled(order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.error(400, "仅已完成或已取消的订单可以删除"));
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

    // 获取所有分类
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<?>> getAllCategories(
            @RequestHeader("Authorization") String authHeader) {
List<Category> categories = categoryRepository.selectList(null);
        
        // 为每个分类添加商品数量
        List<Map<String, Object>> categoriesWithCount = new ArrayList<>();
        for (Category category : categories) {
            Map<String, Object> categoryMap = new HashMap<>();
            categoryMap.put("id", category.getId());
            categoryMap.put("name", category.getName());
            
            // 统计该分类下的商品数量
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Product> productWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            productWrapper.eq("category", category.getName());
            int productCount = productRepository.selectList(productWrapper).size();
            categoryMap.put("productCount", productCount);
            
            categoriesWithCount.add(categoryMap);
        }
        
        return ResponseEntity.ok(ApiResponse.success("获取成功", categoriesWithCount));
    }

    // 添加分类
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<?>> addCategory(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> params) {
String name = params.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "分类名称不能为空"));
        }

        Category category = new Category();
        category.setName(name);
        categoryRepository.insert(category);

        return ResponseEntity.ok(ApiResponse.success("添加成功", category));
    }

    // 更新分类
    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<?>> updateCategory(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, String> params) {
String name = params.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "分类名称不能为空"));
        }

        Category category = categoryRepository.selectById(id);
        if (category == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "分类不存在"));
        }

        category.setName(name);
        categoryRepository.updateById(category);

        return ResponseEntity.ok(ApiResponse.success("更新成功", category));
    }

    // 删除分类
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<?>> deleteCategory(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
Category category = categoryRepository.selectById(id);
        if (category == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "分类不存在"));
        }

        categoryRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
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
}
