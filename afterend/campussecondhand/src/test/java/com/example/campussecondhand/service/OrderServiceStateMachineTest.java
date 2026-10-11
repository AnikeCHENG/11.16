package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.PaymentRecordRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.impl.MockPaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单状态机测试，重点覆盖两条最容易出错的业务约束：
 * 1. 取消订单必须把商品恢复为在售（否则商品被永久锁定）
 * 2. 超时未支付的订单必须能被惰性取消
 *
 * <p>支付相关的用例见 {@link OrderPaymentTest}。</p>
 */
class OrderServiceStateMachineTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private ProductService productService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        productService = mock(ProductService.class);
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                productRepository, userRepository, productService, new OrderFeeService(),
                mock(FundService.class),
                new MockPaymentServiceImpl(), new ResourcelessTransactionTemplate(), 30);

        // 默认：条件 UPDATE "命中"（返回 1 行）、商品抢占成功。
        // 取消/支付/发货现在都靠"条件 UPDATE 影响行数"判成败，不桩默认值的话
        // mock 返回 0，所有正常路径都会被当成并发冲突。需要模拟抢不到的用例
        // 在本方法内单独覆盖。
        when(orderRepository.updateStatusIfMatch(anyLong(), anyInt(), anyInt())).thenReturn(1);
        when(productService.markAsSoldIfOnSale(anyLong())).thenReturn(true);
    }

    private com.example.campussecondhand.entity.Product onSaleProduct() {
        var product = new com.example.campussecondhand.entity.Product();
        product.setId(100L);
        product.setUserId(3L);
        product.setTitle("捷安特山地车");
        product.setPrice(new BigDecimal("1299.00"));
        product.setStatus(ProductStatus.ON_SALE.getCode());
        return product;
    }

    private Order pendingOrder(LocalDateTime expireTime) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("20251006143022001");
        order.setProductId(100L);
        order.setBuyerId(2L);
        order.setSellerId(3L);
        order.setPrice(new BigDecimal("1299.00"));
        order.setShippingFee(new BigDecimal("0.00"));
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setCreatedTime(LocalDateTime.now());
        order.setExpireTime(expireTime);
        return order;
    }

    @Test
    void cancel_restores_product_to_on_sale() {
        Order order = pendingOrder(LocalDateTime.now().plusMinutes(30));
        when(orderRepository.selectById(1L)).thenReturn(order);

        Order cancelled = orderService.cancelOrder(1L);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        // 关键断言：商品必须被释放
        verify(productService).markAsOnSale(100L);
    }

    @Test
    void cancel_rejects_paid_order_so_funds_and_product_stay_consistent() {
        Order order = pendingOrder(LocalDateTime.now().plusMinutes(30));
        order.setStatus(OrderStatus.PENDING_SHIPMENT.getCode());
        when(orderRepository.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("不允许取消");
        // 已付款订单取消失败时绝不能释放商品，否则会出现「货已收钱但商品可再售」
        verify(productService, never()).markAsOnSale(anyLong());
    }

    @Test
    void auto_cancel_is_noop_for_non_pending_order() {
        Order order = pendingOrder(LocalDateTime.now().minusMinutes(1));
        order.setStatus(OrderStatus.COMPLETED.getCode());

        assertThat(orderService.autoCancelIfExpired(order)).isFalse();
        verify(productService, never()).markAsOnSale(anyLong());
    }

    @Test
    void legacy_order_without_expire_time_is_never_treated_as_expired() {
        Order order = pendingOrder(null);

        assertThat(orderService.isExpired(order)).isFalse();
        assertThat(orderService.autoCancelIfExpired(order)).isFalse();
    }

    @Test
    void cashier_total_excludes_service_fee_because_seller_bears_it() {
        Order order = pendingOrder(LocalDateTime.now().plusMinutes(30));
        order.setServiceFee(new BigDecimal("3.90"));
        order.setSellerIncome(new BigDecimal("1295.10"));

        var view = orderService.buildCashierView(order);

        // 买家实付 = 商品金额 + 运费，不含卖家承担的服务费
        assertThat((BigDecimal) view.get("total")).isEqualByComparingTo(new BigDecimal("1299.00"));
        assertThat((BigDecimal) view.get("serviceFee")).isEqualByComparingTo(new BigDecimal("3.90"));
        assertThat(view.get("statusLabel")).isEqualTo("待支付");
    }

    @Test
    void cashier_view_exposes_snapshot_and_remaining_seconds() {
        Order order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setOrderTitle("捷安特山地车");
        order.setOrderImage("data:image/jpeg;base64,AAAA");

        var view = orderService.buildCashierView(order);
        @SuppressWarnings("unchecked")
        var product = (java.util.Map<String, Object>) view.get("product");

        assertThat(product.get("title")).isEqualTo("捷安特山地车");
        assertThat(product.get("image")).isEqualTo("data:image/jpeg;base64,AAAA");
        assertThat((Long) view.get("remainSeconds")).isBetween(1L, 600L);
    }

    @Test
    void remain_seconds_is_zero_for_non_pending_order() {
        Order order = pendingOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus(OrderStatus.CANCELLED.getCode());

        assertThat(orderService.remainSeconds(order)).isZero();
    }

    @Test
    void create_rejects_non_on_sale_product() {
        var product = new com.example.campussecondhand.entity.Product();
        product.setId(100L);
        product.setTitle("已售出的商品");
        product.setPrice(new BigDecimal("10.00"));
        product.setStatus(ProductStatus.SOLD.getCode());
        when(productRepository.selectById(100L)).thenReturn(product);

        assertThatThrownBy(() -> orderService.createOrder(100L, 2L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("已售出");
        // 校验失败时绝不能锁定商品
        verify(productService, never()).markAsSoldIfOnSale(anyLong());
    }

    @Test
    void create_locks_product_with_conditional_update_before_inserting_order() {
        when(productRepository.selectById(100L)).thenReturn(onSaleProduct());
        when(productService.markAsSoldIfOnSale(100L)).thenReturn(true);

        Order created = orderService.createOrder(100L, 2L);

        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());
        assertThat(created.getSellerId()).isEqualTo(3L);
        assertThat(created.getPrice()).isEqualByComparingTo(new BigDecimal("1299.00"));
        // 顺序有意义：必须先抢占商品再落订单，否则会出现"商品已被别人买走、
        // 却留下一条待支付订单"的脏数据
        var ordered = inOrder(productService, orderRepository);
        ordered.verify(productService).markAsSoldIfOnSale(100L);
        ordered.verify(orderRepository).insert(created);
    }

    @Test
    void create_fails_when_conditional_lock_returns_zero_rows() {
        // 竞态的真实形态：selectById 看到的是"在售"（快照已过期），
        // 但数据库里的条件 UPDATE 返回 0 行——商品已经被另一个请求抢走。
        when(productRepository.selectById(100L)).thenReturn(onSaleProduct());
        when(productService.markAsSoldIfOnSale(100L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(100L, 2L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("已被抢先购买");
        // 抢不到就绝不能留下订单
        verify(orderRepository, never()).insert(any(Order.class));
    }

    @Test
    void cancel_gives_up_when_status_update_hits_zero_rows() {
        // 取消前订单还是待支付，但条件 UPDATE 拿到 0 行：买家在毫秒之间支付成功了。
        // 这时必须放弃取消，尤其不能释放商品——钱已收，货必须锁死。
        Order order = pendingOrder(LocalDateTime.now().plusMinutes(30));
        when(orderRepository.selectById(1L)).thenReturn(order);
        when(orderRepository.updateStatusIfMatch(1L, OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.CANCELLED.getCode())).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("状态已变化");
        verify(productService, never()).markAsOnSale(anyLong());
    }

    @Test
    void create_rejects_buying_own_product() {
        var product = new com.example.campussecondhand.entity.Product();
        product.setId(100L);
        product.setUserId(2L);
        product.setStatus(ProductStatus.ON_SALE.getCode());
        when(productRepository.selectById(100L)).thenReturn(product);

        assertThatThrownBy(() -> orderService.createOrder(100L, 2L))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("自己的商品");
    }

    @Test
    void pay_timeout_is_configurable() {
        // 验收测试会把配置改成 1 分钟以加速验证
        OrderService oneMinute = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                productRepository, userRepository, productService, new OrderFeeService(),
                mock(FundService.class),
                new MockPaymentServiceImpl(), new ResourcelessTransactionTemplate(), 1);
        assertThat(oneMinute.getPayTimeoutMinutes()).isEqualTo(1);
        assertThat(orderService.getPayTimeoutMinutes()).isEqualTo(30);
    }
}
