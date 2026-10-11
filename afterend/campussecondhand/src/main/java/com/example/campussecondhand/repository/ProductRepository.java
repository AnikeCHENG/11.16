package com.example.campussecondhand.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campussecondhand.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface ProductRepository extends BaseMapper<Product> {

    /**
     * 条件置为已售出：仅当商品仍处于「在售」时才更新，返回影响行数。
     *
     * <p><b>为什么必须是条件 UPDATE 而不是「先 select 判断、再 update」：</b>
     * 二手商品一件只有一份库存，两个买家同时下单时，两次 select 都会看到
     * status=1，随后两次 update 都成功，于是同一件商品产生两张订单。
     * 把判断与写入合并进同一条 UPDATE，由数据库的行锁保证只有一个请求能把
     * status 从 1 改成 2，另一个拿到 0 行——这才是真正的"抢到/没抢到"。</p>
     *
     * @return 1 表示本次抢到；0 表示已被别人买走或商品已下架
     */
    @Update("UPDATE products SET status = #{soldStatus}, sold_time = NOW(), updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{onSaleStatus}")
    int markSoldIfOnSale(@Param("id") Long id,
                         @Param("onSaleStatus") Integer onSaleStatus,
                         @Param("soldStatus") Integer soldStatus);

    /** 按状态查询，status 传 ProductStatus.getCode()，不在 SQL 中写魔法数字 */
    @Select("SELECT * FROM products WHERE status = #{status} ORDER BY created_time DESC")
    List<Product> findByStatus(@Param("status") Integer status);

    @Select("SELECT * FROM products WHERE user_id = #{userId} ORDER BY created_time DESC")
    List<Product> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM products WHERE category = #{category} AND status = #{status} ORDER BY created_time DESC")
    List<Product> findByCategoryAndStatus(@Param("category") String category, @Param("status") Integer status);

    @Select("SELECT * FROM products WHERE title LIKE CONCAT('%', #{keyword}, '%') AND status = #{status} ORDER BY created_time DESC")
    List<Product> searchByKeywordAndStatus(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("SELECT * FROM products WHERE id = #{id}")
    Product findById(@Param("id") Long id);
}
