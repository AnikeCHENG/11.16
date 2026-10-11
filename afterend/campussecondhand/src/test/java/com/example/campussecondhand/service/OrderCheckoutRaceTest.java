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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同一件二手商品被多人同时抢购时的下单竞态（P0）。
 *
 * <p><b>漏洞是怎么发生的：</b>{@code createOrder} 原本是
 * {@code selectById → 判断 status==在售 → insert 订单 → markAsSold}。
 * "读"与"写"之间没有任何互斥，两个买家同时下单时都会读到"在售"，
 * 于是同一件商品产生两张待支付订单——两个人都付了钱，卖家只有一件货。</p>
 *
 * <p><b>修法：</b>把判断与写入压进一条
 * {@code UPDATE products SET status=已售出 WHERE id=? AND status=在售}，
 * 由数据库行锁保证只有一个请求拿到 1 行；拿到 0 行的请求放弃下单。</p>
 *
 * <p><b>这个测试验证什么、不验证什么：</b>真正的原子性来自数据库的行锁
 * （SQL 的 WHERE 条件由 {@code ConditionalUpdateSqlContractTest} 用反射钉住）。
 * 这里用 {@link AtomicBoolean} 模拟那一次条件 UPDATE 的原子语义，验证的是
 * <b>OrderService 拿到 0 行时确实放弃下单</b>——即"抢不到就不该有订单"这个
 * 应用层契约。少了这个断言，即使 SQL 写对了，调用方忽略返回值照样出两张单。</p>
 */
class OrderCheckoutRaceTest {

    private static final Long PRODUCT_ID = 100L;
    private static final Long SELLER_ID = 3L;
    private static final int BUYERS = 8;

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private ProductService productService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        // ProductService 用真实实现的"半成品"不合适（它要操作 mapper），
        // 这里显式 mock 出 markAsSoldIfOnSale 这个唯一的并发闸门
        productService = mock(ProductService.class);
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                productRepository, mock(UserRepository.class), productService,
                new OrderFeeService(), mock(FundService.class), new MockPaymentServiceImpl(),
                new ResourcelessTransactionTemplate(), 30);
    }

    private Product onSaleProduct() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setUserId(SELLER_ID);
        product.setTitle("只有一件的相机");
        product.setPrice(new BigDecimal("2999.00"));
        product.setStatus(ProductStatus.ON_SALE.getCode());
        return product;
    }

    @Test
    @DisplayName("8 个买家同时抢同一件商品：只有 1 单成功，其余全部被拒")
    void only_one_concurrent_buyer_can_create_the_order() throws Exception {
        when(productRepository.selectById(PRODUCT_ID)).thenReturn(onSaleProduct());

        // 模拟数据库条件 UPDATE 的原子性：第一个调用者把闸门关上，
        // 之后所有调用者都拿到 0 行
        AtomicBoolean claimed = new AtomicBoolean(false);
        when(productService.markAsSoldIfOnSale(PRODUCT_ID))
                .thenAnswer(invocation -> claimed.compareAndSet(false, true));

        AtomicInteger inserted = new AtomicInteger();
        when(orderRepository.insert(any(Order.class))).thenAnswer(invocation -> {
            inserted.incrementAndGet();
            return 1;
        });

        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < BUYERS; i++) {
                final long buyerId = 1000L + i;
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        orderService.createOrder(PRODUCT_ID, buyerId);
                        return true;
                    } catch (OrderService.OrderBusinessException e) {
                        return false;
                    }
                }));
            }
            startGate.countDown();

            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get(10, TimeUnit.SECONDS))) {
                    succeeded++;
                }
            }

            assertThat(succeeded).as("同一件商品只能成交一次").isEqualTo(1);
            assertThat(inserted.get()).as("失败的下单绝不能落库").isEqualTo(1);
            verify(orderRepository, times(1)).insert(any(Order.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("抢不到时抛出可读的业务异常，而不是让调用方拿到 null 订单")
    void losing_buyer_gets_business_exception() {
        when(productRepository.selectById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(productService.markAsSoldIfOnSale(PRODUCT_ID)).thenReturn(false);

        assertThat(catchThrowable()).isInstanceOf(OrderService.OrderBusinessException.class);
        verify(orderRepository, times(0)).insert(any(Order.class));
    }

    private Throwable catchThrowable() {
        try {
            orderService.createOrder(PRODUCT_ID, 1000L);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    @DisplayName("下单不会无条件覆盖商品状态：markAsSold（无条件写入）不再被下单路径调用")
    void checkout_never_uses_unconditional_status_write() {
        when(productRepository.selectById(PRODUCT_ID)).thenReturn(onSaleProduct());
        when(productService.markAsSoldIfOnSale(PRODUCT_ID)).thenReturn(true);

        orderService.createOrder(PRODUCT_ID, 1000L);

        // 无条件版本会把"已售出"硬写回去，覆盖并发取消释放出的"在售"
        verify(productService, times(0)).markAsSold(anyLong());
    }
}
