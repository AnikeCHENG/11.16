package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Review;
import com.example.campussecondhand.enums.CreditEvent;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单评价服务。
 *
 * <p>三条业务约束：仅订单买家本人可评、仅已完成（status=3）可评、一单一评。
 * 前两条是权限与状态校验，第三条同时由应用层与唯一索引 {@code uk_order_id} 保证。</p>
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** 好评线：4 分及以上算好评 */
    private static final int GOOD_RATING = 4;
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_CONTENT_LENGTH = 50;

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    /**
     * 信用分服务：可选注入，走 setter 以保持既有
     * {@code new ReviewService(reviewRepo, orderRepo)} 测试写法不变。
     */
    private CreditService creditService;

    @Autowired(required = false)
    void setCreditService(CreditService creditService) {
        this.creditService = creditService;
    }

    public ReviewService(ReviewRepository reviewRepository, OrderRepository orderRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
    }

    private void recordCredit(Long userId, CreditEvent event, Long bizId, String remark) {
        if (creditService != null) {
            creditService.record(userId, event, bizId, remark);
        }
    }

    /**
     * 提交评价。
     *
     * @param orderId 订单ID
     * @param buyerId 当前登录用户，必须是该订单的买家
     * @param rating 1~5
     * @param content 评价内容，可空但限 50 字
     */
    @Transactional
    public Review submit(Long orderId, Long buyerId, Integer rating, String content) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new BadRequestException("无权评价该订单");
        }
        if (!OrderStatus.isCompleted(order.getStatus())) {
            throw new BadRequestException("订单完成后才能评价");
        }
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new BadRequestException("请选择 1~5 星评分");
        }
        String text = content == null ? null : content.trim();
        if (text != null && text.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("评价内容不能超过 50 字");
        }

        // 先查一次给出友好提示；真正的唯一性由数据库唯一索引兜底
        if (reviewRepository.findByOrderId(orderId) != null) {
            throw new BadRequestException("该订单已评价");
        }

        Review review = new Review();
        review.setOrderId(orderId);
        review.setProductId(order.getProductId());
        review.setReviewerId(buyerId);
        review.setTargetId(order.getSellerId());
        review.setRating(rating);
        review.setContent(text == null || text.isEmpty() ? null : text);

        try {
            reviewRepository.insert(review);
        } catch (DuplicateKeyException e) {
            // 并发双击：两个请求都通过了上面的"已评价"检查，靠唯一索引拦住第二个
            throw new BadRequestException("该订单已评价");
        }

        // 好评 +1，差评（≤2 星）-3，3 星不动。biz_id 用订单ID：一单一评，天然幂等
        if (rating >= GOOD_RATING) {
            recordCredit(order.getSellerId(), CreditEvent.REVIEW_GOOD, orderId,
                    "订单 " + order.getOrderNo() + " 收到 " + rating + " 星评价");
        } else if (rating <= 2) {
            recordCredit(order.getSellerId(), CreditEvent.REVIEW_BAD, orderId,
                    "订单 " + order.getOrderNo() + " 收到 " + rating + " 星评价");
        }

        log.info("订单 {} 收到评价：{} 星", orderId, rating);
        return review;
    }

    /** 订单的评价回显；未评价返回 null */
    public Review findByOrderId(Long orderId) {
        return reviewRepository.findByOrderId(orderId);
    }

    /**
     * 卖家评价统计，SQL 聚合实时计算，不存冗余字段。
     *
     * <p>评价提交后不可修改也不可删除，因此不存在"统计值需要同步"的问题；
     * 冗余字段反而要处理并发更新导致的漂移。</p>
     */
    public Map<String, Object> statsOf(Long sellerId) {
        Map<String, Object> row = reviewRepository.aggregateByTargetId(sellerId);

        long total = toLong(row == null ? null : row.get("total"));
        long good = toLong(row == null ? null : row.get("good"));
        BigDecimal avg = row == null || row.get("avg_rating") == null
                ? BigDecimal.ZERO
                : new BigDecimal(row.get("avg_rating").toString()).setScale(1, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("good", good);
        result.put("averageRating", avg);
        // 无评价时好评率返回 0 而不是 null：前端直接展示 "0%"
        result.put("goodRate", total == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(good)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        result.put("hasReview", total > 0);
        return result;
    }

    /**
     * 卖家收到的文字评价（最近 N 条），供商品详情页展示。
     *
     * <p>limit 在服务层收敛到 1~20：调用方传 null、0 或超大值都不会
     * 变成全表扫描。没有评价时返回空列表而不是 null，前端可直接 v-for。</p>
     */
    public List<Map<String, Object>> listForSeller(Long sellerId, Integer limit) {
        if (sellerId == null) {
            return List.of();
        }
        int size = limit == null ? 5 : Math.max(1, Math.min(limit, 20));
        return reviewRepository.listByTargetId(sellerId, size);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}