package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.common.PageResult;
import java.util.Objects;
import java.util.stream.Collectors;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.enums.ConditionLevel;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.BrowseHistoryService;
import com.example.campussecondhand.service.ProductService;
import com.example.campussecondhand.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private BrowseHistoryService browseHistoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 大厅商品列表（物理分页）。
     *
     * <p>筛选与排序下推到 SQL，与分页在同一条语句内完成；
     * 若只分页而把筛选留在前端，结果只会覆盖当前页。
     * 卖家信息按当页 id 批量补齐，避免每条商品一次 selectById 的 N+1。</p>
     */
@GetMapping("/list")
    public ResponseEntity<ApiResponse<?>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer conditionLevel,
            @RequestParam(required = false, defaultValue = "newest") String sort) {

        PageParam paging = PageParam.of(page, size);
        IPage<Product> paged = productService.pageAvailable(
                paging, category, keyword, minPrice, maxPrice, conditionLevel, sort);

        List<Long> sellerIds = paged.getRecords().stream()
                .map(Product::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!sellerIds.isEmpty()) {
            Map<Long, User> sellers = userRepository.selectBatchIds(sellerIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            paged.getRecords().forEach(p -> p.setSeller(sellers.get(p.getUserId())));
        }

        return ResponseEntity.ok(ApiResponse.success("获取成功", PageResult.of(paged)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<?>> myProducts(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.getUsernameFromToken(token);
            if (username == null) {
                return ResponseEntity.ok(ApiResponse.error(401, "无效的token"));
            }
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
            }
            List<Product> products = productService.findByUserId(userOpt.get().getId());
            return ResponseEntity.ok(ApiResponse.success("获取成功", products));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(401, "无效的token"));
        }
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<?>> byCategory(@PathVariable String category) {
        List<Product> products = productService.findByCategory(category);
        products.forEach(p -> {
            User seller = userRepository.selectById(p.getUserId());
            if (seller != null) p.setSeller(seller);
        });
        return ResponseEntity.ok(ApiResponse.success("获取成功", products));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<?>> search(@RequestParam String keyword) {
        List<Product> products = productService.searchByKeyword(keyword);
        products.forEach(p -> {
            User seller = userRepository.selectById(p.getUserId());
            if (seller != null) p.setSeller(seller);
        });
        return ResponseEntity.ok(ApiResponse.success("获取成功", products));
    }

    /**
     * 商品详情。
     *
     * <p>浏览历史在此顺带记录，但 {@code Authorization} 必须是<b>可选</b>请求头：
     * 商品详情允许游客访问，若设为必填会把未登录访客直接挡在 401。
     * 解析不到用户就跳过记录，绝不影响详情本身的返回。</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Product product = productService.findByIdWithViewCount(id);
        if (product != null) {
            User seller = userRepository.selectById(product.getUserId());
            if (seller != null) product.setSeller(seller);
            recordBrowseHistory(authHeader, id);
            return ResponseEntity.ok(ApiResponse.success("获取成功", product));
        }
        return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
    }

    /**
     * 记录浏览历史。未登录或 token 无效时静默跳过。
     *
     * <p>整段包 try-catch：历史记录属于附带能力，绝不能因为它失败
     * 而让用户看不到商品详情。</p>
     */
    private void recordBrowseHistory(String authHeader, Long productId) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return;
            }
            String username = jwtUtil.getUsernameFromToken(authHeader.substring(7));
            if (username == null) {
                return;
            }
            Optional<User> userOpt = userRepository.findByUsername(username);
            userOpt.ifPresent(user -> browseHistoryService.recordView(user.getId(), productId));
        } catch (Exception e) {
            // 静默忽略：不影响详情返回
        }
    }

@PostMapping("/create")
    public ResponseEntity<ApiResponse<?>> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> params) {
        try {
            // 从 token 中获取当前登录用户
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.getUsernameFromToken(token);
            if (username == null) {
                return ResponseEntity.ok(ApiResponse.error(401, "无效的token"));
            }
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
            }
            Long userId = userOpt.get().getId();

            // 成色必填：二手交易的核心信息，缺失会让买家无法判断商品价值。
            // 校验放在入库前，避免脏数据靠数据库 DEFAULT 2 兜底——那等于替卖家编造成色。
            Object rawLevel = params.get("conditionLevel");
            if (rawLevel == null || String.valueOf(rawLevel).isBlank()) {
                return ResponseEntity.ok(ApiResponse.error(400, "请选择商品成色"));
            }
            Integer conditionLevel;
            try {
                conditionLevel = Integer.valueOf(String.valueOf(rawLevel).trim());
            } catch (NumberFormatException e) {
                return ResponseEntity.ok(ApiResponse.error(400, "商品成色格式不正确"));
            }
            if (!ConditionLevel.isValid(conditionLevel)) {
                return ResponseEntity.ok(ApiResponse.error(400, "商品成色取值不合法"));
            }

            // 瑕疵说明可空，但超长会被数据库截断，先在这里拦住
            String flawDescription = params.get("flawDescription") == null
                    ? null
                    : String.valueOf(params.get("flawDescription")).trim();
            if (flawDescription != null && flawDescription.length() > 255) {
                return ResponseEntity.ok(ApiResponse.error(400, "瑕疵说明不能超过 255 个字符"));
            }

            Product product = new Product();
            product.setUserId(userId);
            product.setTitle((String) params.get("title"));
            product.setDescription((String) params.get("description"));
            product.setPrice(new BigDecimal(params.get("price").toString()));
            if (params.get("originalPrice") != null) {
                product.setOriginalPrice(new BigDecimal(params.get("originalPrice").toString()));
            }
            product.setCategory((String) params.get("category"));
            product.setConditionLevel(conditionLevel);
            // 空字符串归一为 null：前端若传 ""，数据库里应是 NULL 而不是空串，
            // 否则"是否填写了瑕疵"这个判断要同时处理两种空值
            product.setFlawDescription(flawDescription == null || flawDescription.isEmpty()
                    ? null : flawDescription);
            product.setImages((String) params.get("images"));

            Product created = productService.create(product);
            return ResponseEntity.ok(ApiResponse.success("发布成功", created));
        } catch (Exception e) {
            log.error("发布商品失败: userId 见请求上下文", e);
            return ResponseEntity.ok(ApiResponse.error(500, "发布失败，请稍后重试"));
        }
    }

    /**
     * 编辑商品：<b>仅商品归属者本人</b>，且已成交的商品不允许改动价格。
     *
     * <p><b>此前本端点没有任何鉴权</b>（不读 {@code Authorization}、不校验归属），
     * 而 {@code SecurityConfig} 是 {@code .anyRequest().permitAll()}，
     * 于是任何人——包括未登录访客——都能改掉任意在售商品的标题、价格、
     * 描述和图片，把 12999 元的相机改成 1 元。这是本项目最容易被利用的漏洞，
     * 因为不需要注册、不需要时序、一条 curl 就能复现。</p>
     *
     * <p>价格额外单独上锁：商品已售出，或存在进行中的订单（待支付/待发货/待收货）
     * 时，价格已经是订单与支付流水的凭证，改动它会让订单金额与商品详情对不上。</p>
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<?>> update(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> params) {
        try {
            Optional<User> userOpt = resolveUser(authHeader);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "未登录或登录已过期"));
            }
            Long currentUserId = userOpt.get().getId();

            Product product = productService.findById(id);
            if (product == null) {
                return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
            }
            // 归属校验：不是自己的商品一律 403，且不透露价格等细节
            if (!Objects.equals(product.getUserId(), currentUserId)) {
                return ResponseEntity.ok(ApiResponse.error(403, "无权操作该商品"));
            }

            BigDecimal newPrice = null;
            if (params.get("price") != null) {
                try {
                    newPrice = new BigDecimal(String.valueOf(params.get("price")).trim());
                } catch (NumberFormatException e) {
                    return ResponseEntity.ok(ApiResponse.error(400, "价格格式不正确"));
                }
                if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.ok(ApiResponse.error(400, "价格必须大于 0"));
                }
                // 已售出 / 有进行中订单：价格已写入订单与流水，禁止改动（传相同价格不算改动）
                boolean priceChanged = product.getPrice() == null
                        || newPrice.compareTo(product.getPrice()) != 0;
                if (priceChanged && isPriceLocked(product)) {
                    return ResponseEntity.ok(ApiResponse.error(403,
                            "商品已售出或有进行中的订单，不能修改价格"));
                }
            }

            if (params.get("title") != null) product.setTitle((String) params.get("title"));
            if (params.get("description") != null) product.setDescription((String) params.get("description"));
            if (newPrice != null) product.setPrice(newPrice);
            if (params.get("originalPrice") != null) product.setOriginalPrice(new BigDecimal(params.get("originalPrice").toString()));
if (params.get("category") != null) product.setCategory((String) params.get("category"));
            // 成色与瑕疵同样允许编辑，但等级必须落在 0~4，避免前端传脏值写进库
            if (params.get("conditionLevel") != null) {
                Integer level = Integer.valueOf(String.valueOf(params.get("conditionLevel")).trim());
                if (!ConditionLevel.isValid(level)) {
                    return ResponseEntity.ok(ApiResponse.error(400, "商品成色取值不合法"));
                }
                product.setConditionLevel(level);
            }
            if (params.containsKey("flawDescription")) {
                String flaw = params.get("flawDescription") == null ? null
                        : String.valueOf(params.get("flawDescription")).trim();
                if (flaw != null && flaw.length() > 255) {
                    return ResponseEntity.ok(ApiResponse.error(400, "瑕疵说明不能超过 255 个字符"));
                }
                product.setFlawDescription(flaw == null || flaw.isEmpty() ? null : flaw);
            }
            if (params.get("images") != null) product.setImages((String) params.get("images"));

            Product updated = productService.update(product);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updated));
        } catch (Exception e) {
            // 详情只进服务端日志：异常消息可能含 SQL 片段、表名或约束名，
            // 拼进响应体等于免费给攻击者做信息收集
            log.error("更新商品失败: id={}", id, e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败，请稍后重试"));
        }
    }

    /**
     * 解析 {@code Authorization} 头为当前用户；缺失或无效返回 empty。
     *
     * <p>与 {@code OrderController#getUserFromToken} 行为一致，
     * 供本类的写接口复用，避免每个方法各写一遍 try-catch。</p>
     */
    private Optional<User> resolveUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            String username = jwtUtil.getUsernameFromToken(authHeader.substring(7));
            if (username == null) {
                return Optional.empty();
            }
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 商品是否"价格已定"：已售出，或存在进行中的订单。
     *
     * <p>进行中 = 待支付/待发货/待收货。已取消、已完成的订单不再锁定商品，
     * 与 {@code OrderStatus} 语义保持一致。</p>
     */
    private boolean isPriceLocked(Product product) {
        if (ProductStatus.isSold(product.getStatus())) {
            return true;
        }
        Long active = orderRepository.selectCount(new QueryWrapper<Order>()
                .eq("product_id", product.getId())
                .in("status", OrderStatus.PENDING_PAYMENT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode(),
                        OrderStatus.PENDING_RECEIPT.getCode()));
        return active != null && active > 0;
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<?>> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.getUsernameFromToken(token);
            if (username == null) {
                return ResponseEntity.ok(ApiResponse.error(401, "无效的token"));
            }
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
            }

            Product product = productService.findById(id);
            if (product == null) {
                return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
            }
            if (!product.getUserId().equals(userOpt.get().getId())) {
                return ResponseEntity.ok(ApiResponse.error(403, "无权操作"));
            }

            int deleted = productService.delete(id);
            if (deleted > 0) {
                return ResponseEntity.ok(ApiResponse.success("删除成功", null));
            }
            return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败"));
        }
    }

    @PutMapping("/sold/{id}")
    public ResponseEntity<ApiResponse<?>> markAsSold(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.getUsernameFromToken(token);
            if (username == null) {
                return ResponseEntity.ok(ApiResponse.error(401, "无效的token"));
            }
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
            }

            Product product = productService.findById(id);
            if (product != null && !product.getUserId().equals(userOpt.get().getId())) {
                return ResponseEntity.ok(ApiResponse.error(403, "无权操作"));
            }

            boolean success = productService.markAsSold(id);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("已标记为已售出", null));
            }
            return ResponseEntity.ok(ApiResponse.error(404, "商品不存在"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "操作失败"));
        }
    }

    // 原先这里有一个 @PostMapping("/update-images") 图片迁移端点，已删除。
    //
    // 它没有任何鉴权，而 SecurityConfig 是 .anyRequest().permitAll()，
    // 也就是说未登录的人一封 POST 就能调用它。端点逻辑是「遍历全部在售商品，
    // 标题不在请求体 imageNames 里的，一律 productService.delete()」，
    // 于是 POST {"imageNames":["任意一个不存在的标题"]} 会把整个商品目录清空。
    // 一次请求、结果确定、无需登录——比任何竞态漏洞都更容易触发。
    //
    // 它本身也只是个一次性数据修补工具（图片路径写死 /src/images/），没有运行期用途。
    // 将来若确需重跑，应仿照 DevMigrationController 放到 @Profile("dev") 下，
    // 而不是放回公开接口。删除理由见 ProductControllerUnauthenticatedWriteTest。
}
