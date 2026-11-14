package com.example.campussecondhand.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campussecondhand.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewRepository extends BaseMapper<Review> {

    @Select("SELECT * FROM review WHERE order_id = #{orderId} LIMIT 1")
    Review findByOrderId(@Param("orderId") Long orderId);

    /**
     * 卖家评价聚合。
     *
     * <p>一条 SQL 算完好评率、平均星级与总数，而不是查三次再在内存里算：
     * 多次查询之间若有并发写入，三个数字可能来自不同瞬间，对不上账。</p>
     *
     * <p>{@code COALESCE(SUM(...), 0)} 处理"从未被评价"的卖家——
     * 无记录时 SUM 返回 NULL，直接除会得到 null 而前端会显示 "null%"。</p>
     */
    @Select("""
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END), 0) AS good,
               COALESCE(AVG(rating), 0) AS avg_rating
        FROM review
        WHERE target_id = #{sellerId}
        """)
    Map<String, Object> aggregateByTargetId(@Param("sellerId") Long sellerId);

    /**
     * 卖家收到的评价列表（最近 N 条）。
     *
     * <p>只取有文字内容的评价：纯星级（content 为空）在商品详情页显示为
     * 一行空白，对买家没有信息量，反而会让"评价列表"看起来像加载失败。
     * 时间在 SQL 里格式化，避免 JDBC Timestamp 被序列化成时间戳数字。
     * {@code LIMIT} 的上限由 Service 层收敛，这里不做业务判断。</p>
     */
    @Select("""
        SELECT r.id,
               r.rating,
               r.content,
               DATE_FORMAT(r.create_time, '%Y-%m-%d %H:%i') AS createTime,
               r.product_id AS productId,
               u.username AS reviewerName
        FROM review r
        LEFT JOIN users u ON u.id = r.reviewer_id
        WHERE r.target_id = #{sellerId}
          AND r.content IS NOT NULL
          AND r.content <> ''
        ORDER BY r.create_time DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> listByTargetId(@Param("sellerId") Long sellerId,
                                             @Param("limit") int limit);
}