package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.PaymentRecordRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.impl.MockPaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单撞唯一键时的处理（P1-11）。
 *
 * <p><b>修复前：</b>{@code catch (DuplicateKeyException) { 换订单号; 重试一次 }} ——
 * 把<b>所有</b>唯一键冲突都当成"订单号撞车"。加了 {@code uk_active_product}
 * （一件商品同时只能有一张未取消订单，见 migration-order-unique-active.sql）之后，
 * "同一商品重复下单"的冲突会被无意义地重试一次、必然再撞，
 * 最终以数据库异常的形式冒到接口上，用户看到的是 500 而不是"别重复下单"。</p>
 *
 * <p>现在按报错里的索引名分流：订单号冲突才重试；商品唯一约束直接给业务提示；
 * 认不出的约束既不重试、也不硬翻译成"商品已有订单"（打日志 + 笼统话术）。</p>
 */
class OrderDuplicateKeyTest {

    private static final Long PRODUCT_ID = 100L;

    private OrderRepository orderRepository;
    private ProductService productService;
    private OrderService orderService;

    /** 每次 insert 时订单号的实际取值，用来观察"是否换了号重试" */
    private final List<String> attemptedOrderNos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        productService = mock(ProductService.class);
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                productRepository, mock(UserRepository.class), productService,
                new OrderFeeService(), mock(FundService.class), new MockPaymentServiceImpl(),
                new ResourcelessTransactionTemplate(), 30);
        attemptedOrderNos.clear();

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setUserId(3L);
        product.setTitle("只有一件的相机");
        product.setPrice(new BigDecimal("2999.00"));
        product.setStatus(ProductStatus.ON_SALE.getCode());
        when(productRepository.selectById(PRODUCT_ID)).thenReturn(product);
        when(productService.markAsSoldIfOnSale(PRODUCT_ID)).thenReturn(true);
    }

    /** 让第 failTimes 次之前的 insert 抛指定唯一键冲突，之后成功返回 1 */
    private void failFirstInserts(int failTimes, String key) {
        when(orderRepository.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            attemptedOrderNos.add(order.getOrderNo());
            if (attemptedOrderNos.size() <= failTimes) {
                throw new DuplicateKeyException(
                        "Duplicate entry '" + order.getOrderNo() + "' for key '" + key + "'");
            }
            return 1;
        });
    }

    @Test
    @DisplayName("订单号撞车：换一个号重试一次并成功")
    void order_no_collision_is_retried_once() {
        failFirstInserts(1, "orders.uk_order_no");

        Order created = orderService.createOrder(PRODUCT_ID, 2L);

        assertThat(created).isNotNull();
        assertThat(attemptedOrderNos).hasSize(2);
        // 重试用的是新订单号（同一秒内随机数极小概率相同，故只断言"确实提交了两次"）
        assertThat(created.getOrderNo()).isEqualTo(attemptedOrderNos.get(1));
        verify(orderRepository, times(2)).insert(any(Order.class));
    }

    @Test
    @DisplayName("商品唯一约束冲突：不重试，直接给业务提示")
    void product_unique_conflict_is_not_retried() {
        failFirstInserts(99, "orders.uk_active_product");

        assertThatThrownBy(() -> orderService.createOrder(PRODUCT_ID, 2L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("已有未完成的订单");
        // 关键：只尝试了一次，没有拿同一个冲突去撞第二次
        verify(orderRepository, times(1)).insert(any(Order.class));
    }

    @Test
    @DisplayName("订单号连续两次撞车：干净失败，不把数据库异常抛给调用方")
    void repeated_order_no_collision_fails_as_business_error() {
        failFirstInserts(99, "orders.uk_order_no");

        assertThatThrownBy(() -> orderService.createOrder(PRODUCT_ID, 2L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("下单失败");
        verify(orderRepository, times(2)).insert(any(Order.class));
    }

    @Test
    @DisplayName("认不出的唯一约束：不重试，也不谎称是商品重复")
    void unknown_unique_conflict_is_not_misreported() {
        failFirstInserts(99, "orders.unexpected_constraint");

        assertThatThrownBy(() -> orderService.createOrder(PRODUCT_ID, 2L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("下单失败")
                .hasMessageNotContaining("已有未完成的订单");
        verify(orderRepository, times(1)).insert(any(Order.class));
    }
}
