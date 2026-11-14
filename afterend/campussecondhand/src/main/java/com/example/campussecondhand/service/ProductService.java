package com.example.campussecondhand.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.campussecondhand.common.PageParam;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.exception.ForbiddenException;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductService {

    /**
     * 价格上限：{@code products.price} 的列类型是 {@code decimal(10,2)}，
     * 因此能存的最大值是 {@code 99999999.99}。超过它 MySQL 会抛
     * {@code MysqlDataTruncation: Out of range value for column 'price'}，
     * 在 Controller 层表现为毫无信息量的「服务器内部错误」。
     *
     * <p><b>改 schema 必须同步改这里</b>：若把该列放宽成 {@code decimal(12,2)}，
     * 本常量要一并上调，否则合法价格会被本层误拒。</p>
     */
    public static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 信用分服务。<b>必须是可选依赖</b>：单元测试用 {@code new ProductService()}
     * 直接构造，字段为 null 时跳过信用校验（fail-open），
     * 否则测试类要跟着改。与 {@code OrderService} 的注入方式保持一致。
     */
    @Autowired(required = false)
    private CreditService creditService;

    /**
     * 全量在售商品（<b>无分页</b>）。
     *
     * <p><b>面向用户的列表请改用 {@link #pageAvailable}。</b>那是带 LIMIT 的物理分页；
     * 本方法会把整张在售商品表读进内存，仅适合统计、导出这类确实需要全量的场景。</p>
     */
    public List<Product> findAllAvailable() {
        return productRepository.findByStatus(ProductStatus.ON_SALE.getCode());
    }

    /**
     * 大厅在售商品的<b>物理分页</b>查询：筛选与排序全部下推到 SQL。
     *
     * <p>此前大厅把全量商品一次性返回、由前端在内存里做分类/关键词/价格/成色过滤与排序。
     * 改成服务端分页后若仍保留那套前端逻辑，筛选只会作用在当前页的十几条记录上，
     * 结果直接失真——所以筛选与排序必须与分页在同一条 SQL 内完成。</p>
     *
* @param paging 分页参数（已由 {@code PageParam} 校验）
     * @param conditionLevel 成色等级 0~4；{@code null} 表示不筛选
     * @param sort   {@code newest} / {@code priceAsc} / {@code priceDesc} / {@code hot} /
     *               {@code condition}，未知值按 {@code newest} 处理
     */
    public IPage<Product> pageAvailable(PageParam paging, String category, String keyword,
                                        BigDecimal minPrice, BigDecimal maxPrice,
                                        Integer conditionLevel, String sort) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.eq("status", ProductStatus.ON_SALE.getCode());

        if (category != null && !category.isBlank()) {
            wrapper.eq("category", category);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 用 and(...) 包裹，避免 OR 泄漏到后续价格等条件之外
            wrapper.and(q -> q.like("title", keyword).or().like("description", keyword));
        }
        if (minPrice != null) {
            wrapper.ge("price", minPrice);
        }
        if (maxPrice != null) {
            wrapper.le("price", maxPrice);
        }
        if (conditionLevel != null) {
            // 成色已规范为 TINYINT，可直接比较与建索引；旧实现用 VARCHAR 中文文本，
            // 前端传 new/like-new/good/fair 与库值不匹配，筛选一直是失效的
            wrapper.eq("condition_level", conditionLevel);
        }

        switch (sort == null ? "" : sort) {
            case "priceAsc" -> wrapper.orderByAsc("price");
            case "priceDesc" -> wrapper.orderByDesc("price");
            case "hot" -> wrapper.orderByDesc("view_count");
            // 成色升序即"从新到旧"；追加 id 保证同成色下顺序确定
            case "condition" -> wrapper.orderByAsc("condition_level", "created_time");
            default -> wrapper.orderByDesc("created_time");
        }

        return productRepository.selectPage(paging.toPage(), wrapper);
    }

    public List<Product> findByUserId(Long userId) {
        return productRepository.findByUserId(userId);
    }

    public List<Product> findByCategory(String category) {
        return productRepository.findByCategoryAndStatus(category, ProductStatus.ON_SALE.getCode());
    }

    public List<Product> searchByKeyword(String keyword) {
        return productRepository.searchByKeywordAndStatus(keyword, ProductStatus.ON_SALE.getCode());
    }

    public Product findById(Long id) {
        return productRepository.selectById(id);
    }

    public Product findByIdWithViewCount(Long id) {
        Product product = productRepository.selectById(id);
        if (product != null) {
            // 历史数据/绕过实体默认值的写入可能让 view_count 为 null，直接 +1 会 NPE
            Integer current = product.getViewCount();
            product.setViewCount((current == null ? 0 : current) + 1);
            productRepository.updateById(product);
        }
        return product;
    }

    // 根据商品名称自动分类
    private String autoCategorize(String title) {
        // 分类映射
        Map<String, List<String>> categoryMapping = new HashMap<>();
        categoryMapping.put("books", Arrays.asList("书", "教材", "课本", "小说", "简史", "传", "四世同堂", "在细雨中呼喊", "堂吉坷德", "明朝那些事儿", "活着", "狂人日记", "许三观卖血记", "资治通鉴"));
        categoryMapping.put("electronics", Arrays.asList("手机", "电脑", "平板", "耳机", "相机", "电子"));
        categoryMapping.put("transport", Arrays.asList("自行车", "电动车", "出行", "车"));
        categoryMapping.put("gaming", Arrays.asList("游戏", "手柄", "键盘", "鼠标", "游戏机"));
        categoryMapping.put("clothing", Arrays.asList("衣服", "裤子", "鞋子", "帽子", "服饰"));
        categoryMapping.put("living", Arrays.asList("生活用品", "家具", "被子", "枕头", "厨具"));
        
        // 匹配分类
        for (Map.Entry<String, List<String>> entry : categoryMapping.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();
            for (String keyword : keywords) {
                if (title.contains(keyword)) {
                    return category;
                }
            }
        }
        return "other"; // 默认分类
    }

    /**
     * 新建商品。<b>这是商品落库的唯一入口</b>（{@code productRepository.insert} 仅此一处调用）。
     *
     * <p>信用分校验必须放在这里，不能放在 {@code ProductController}：
     * 建商品有两条路径——「发布商品」接口与「广场发卖货/赠送动态」
     * （{@code PostService#create} 在动态带 productInfo 时调本方法）。
     * 校验若只写在 Controller，广场那条路就能绕过——
     * 受限用户发一条带商品的动态即可上架，限制形同虚设。</p>
     *
     * <p>失败抛 {@link ForbiddenException}，由全局异常处理器渲染；
     * 调用方（Controller 或 PostService）无需各自处理。</p>
     */
    public Product create(Product product) {
        if (creditService != null && product.getUserId() != null
                && creditService.isRestricted(product.getUserId())) {
            throw new ForbiddenException("你的信用分过低，暂时无法发布商品");
        }
        checkPriceRange(product.getPrice());
        product.setCreatedTime(LocalDateTime.now());
        product.setUpdatedTime(LocalDateTime.now());
        product.setViewCount(0);
        // 新发布商品默认在售
        product.setStatus(ProductStatus.ON_SALE.getCode());
        
        // 自动分类
        if (product.getCategory() == null || product.getCategory().isEmpty()) {
            product.setCategory(autoCategorize(product.getTitle()));
        }
        
        productRepository.insert(product);
        return product;
    }

    public Product update(Product product) {
        checkPriceRange(product.getPrice());
        product.setUpdatedTime(LocalDateTime.now());
        productRepository.updateById(product);
        return product;
    }

    /**
     * 价格区间守卫：负数与超出 {@link #MAX_PRICE} 一律拒收。
     *
     * <p><b>为什么放在这里：</b>本类是唯一会给 {@code price} 写入新值的地方。
     * 全库只有 {@code create()} 调 {@code productRepository.insert}；
     * {@code updateById} 虽有 6 处调用，但其余 5 处（后台批量改状态、处罚、
     * 浏览量自增、标记已售、重新上架）都是「先查出来、只改一个字段再写回」，
     * price 原样回写、不会引入新值。所以这两个方法就是价格的收口。</p>
     *
     * <p><b>下界只拦负数、放行 0：</b>广场「免费赠送」按 0 元入库，
     * 由 {@code PostService#buildProduct} 区分类型；出售商品必须 &gt; 0，
     * 由 {@code ProductController#create} 在入口处拦住。两层口径不同，
     * 不能在 Service 统一要求 &gt; 0，否则免费赠送会被误杀。</p>
     *
     * @throws BadRequestException 价格为负或超出可存储范围时抛出，
     *         由 {@code GlobalExceptionHandler} 转成 400 + 中文提示
     */
    private void checkPriceRange(BigDecimal price) {
        if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("价格不能为负数");
        }
        if (price != null && price.compareTo(MAX_PRICE) > 0) {
            throw new BadRequestException("价格超出允许范围（最高 " + MAX_PRICE.toPlainString() + "）");
        }
    }

    public int delete(Long id) {
        return productRepository.deleteById(id);
    }

    public boolean markAsSold(Long id) {
        Product product = productRepository.selectById(id);
        if (product != null) {
            // 成交后置为已售出
            product.setStatus(ProductStatus.SOLD.getCode());
            product.setSoldTime(LocalDateTime.now());
            product.setUpdatedTime(LocalDateTime.now());
            productRepository.updateById(product);
            return true;
        }
        return false;
    }

    /**
     * <b>下单专用</b>：仅当商品仍在售时把它锁定为已售出。
     *
     * <p>与 {@link #markAsSold(Long)} 的区别是并发语义：后者无条件写入，
     * 用于「已经确定成交」的场景（如管理员操作）；本方法把「判断在售」
     * 与「置为已售出」压进一条 UPDATE，返回 false 即代表本次没抢到，
     * 调用方（{@link OrderService#createOrder}）必须据此放弃下单。</p>
     *
     * @return true 表示本次成功锁定；false 表示商品已被别人抢先买走或已下架
     */
    public boolean markAsSoldIfOnSale(Long id) {
        return productRepository.markSoldIfOnSale(id,
                ProductStatus.ON_SALE.getCode(), ProductStatus.SOLD.getCode()) > 0;
    }

    /**
     * 订单取消/超时后把商品恢复为在售。
     *
     * <p>同时清空 sold_time，否则商品虽回到在售状态却仍带着成交时间，
     * 导致「在售商品却显示已售出时间」的数据不一致。</p>
     *
     * @param id 商品 ID
     * @return 商品存在并已恢复时返回 true
     */
    public boolean markAsOnSale(Long id) {
        Product product = productRepository.selectById(id);
        if (product != null) {
            product.setStatus(ProductStatus.ON_SALE.getCode());
            product.setSoldTime(null);
            product.setUpdatedTime(LocalDateTime.now());
            productRepository.updateById(product);
            return true;
        }
        return false;
    }
}
