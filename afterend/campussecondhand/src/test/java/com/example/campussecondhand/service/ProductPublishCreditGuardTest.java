package com.example.campussecondhand.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campussecondhand.entity.Post;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.exception.ForbiddenException;
import com.example.campussecondhand.repository.CreditRepository;
import com.example.campussecondhand.repository.PostRepository;
import com.example.campussecondhand.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 信用受限用户的「禁止发布商品」守卫。
 *
 * <p><b>这个洞是怎么漏出去的：</b>限制最初只写在 {@code ProductController} 的发布接口里，
 * 但建商品有两条路径——发布接口，以及广场发「卖货/赠送」动态时由
 * {@code PostService#create} 联动建商品（{@code PostService:114}）。
 * 后者不经过那个 Controller，于是信用分 55 的用户只要在广场发一条带商品的动态，
 * 照样能上架，限制形同虚设。前端 {@code Plaza.vue} 正好提供了这个入口的可视化表单。</p>
 *
 * <p>修复方式是把守卫下沉到 {@code ProductService#create}——全库唯一调用
 * {@code productRepository.insert} 的地方，两条路径都绕不过去。</p>
 *
 * <p>本测试刻意装配<b>真实</b>的 {@code ProductService}/{@code PostService}，
 * 只把仓储与信用分读取换成 mock：要验证的正是真实链路上守卫生效，
 * 若把 Service 也换成 mock，测试会假通过。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductPublishCreditGuardTest {

    /** 与信用等级 RESTRICTED 的分界一致：低于 60 才受限 */
    private static final int RESTRICTED_SCORE = 55;
    private static final int BOUNDARY_SCORE = 60;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PostRepository postRepository;
    /** 只 mock 取分，CreditService 本体用真的 */
    @Mock
    private CreditRepository creditRepository;

    private ProductService productService;
    private PostService postService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
        ReflectionTestUtils.setField(productService, "productRepository", productRepository);
        ReflectionTestUtils.setField(productService, "creditService", new CreditService(creditRepository));

        postService = new PostService();
        ReflectionTestUtils.setField(postService, "postRepository", postRepository);
        ReflectionTestUtils.setField(postService, "productService", productService);

        when(postRepository.insert(any(Post.class))).thenReturn(1);
        when(productRepository.insert(any(Product.class))).thenReturn(1);
    }

    private void givenScore(int score) {
        when(creditRepository.scoreOf(anyLong())).thenReturn(score);
    }

    private Product newProduct() {
        Product p = new Product();
        p.setUserId(7L);
        p.setTitle("闲置笔记本");
        p.setDescription("九成新");
        p.setPrice(new BigDecimal("1999.00"));
        return p;
    }

    private PostService.CreateCommand sellCommand() {
        return new PostService.CreateCommand(
                "SELL", "出一台闲置笔记本", null, null,
                "闲置笔记本", new BigDecimal("1999.00"), null, null);
    }

    /* ---------------- 路径一：发布商品接口 ---------------- */

    @Test
    @DisplayName("受限用户发布商品：抛 ForbiddenException，商品不落库")
    void restricted_user_cannot_create_product() {
        givenScore(RESTRICTED_SCORE);

        assertThatThrownBy(() -> productService.create(newProduct()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("信用分过低");

        verify(productRepository, never()).insert(any(Product.class));
    }

    @Test
    @DisplayName("临界 60 分可以发布（59 才是受限）")
    void boundary_score_is_allowed() {
        givenScore(BOUNDARY_SCORE);

        assertThatCode(() -> productService.create(newProduct())).doesNotThrowAnyException();
        verify(productRepository).insert(any(Product.class));
    }

    /* ---------------- 路径二：广场卖货动态（修复前从这里绕过） ---------------- */

    @Test
    @DisplayName("受限用户发广场卖货动态：同样被拦，商品与动态都不落库")
    void restricted_user_cannot_create_product_via_plaza_post() {
        givenScore(RESTRICTED_SCORE);

        assertThatThrownBy(() -> postService.create(7L, sellCommand()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("信用分过低");

        // 两条 insert 都不能发生：动态与商品要么一起成功，要么都不留痕
        verify(productRepository, never()).insert(any(Product.class));
        verify(postRepository, never()).insert(any(Post.class));
    }

    @Test
    @DisplayName("未受限用户走广场卖货动态：正常建商品并建动态")
    void normal_user_can_create_product_via_plaza_post() {
        givenScore(BOUNDARY_SCORE);

        assertThatCode(() -> postService.create(7L, sellCommand())).doesNotThrowAnyException();
        verify(productRepository).insert(any(Product.class));
        verify(postRepository).insert(any(Post.class));
    }

    /* ---------------- fail-open：读不到分必须放行 ---------------- */

    @Test
    @DisplayName("未注入 CreditService（如切片测试）：放行，不影响原有功能")
    void missing_credit_service_fails_open() {
        ProductService bare = new ProductService();
        ReflectionTestUtils.setField(bare, "productRepository", productRepository);

        assertThatCode(() -> bare.create(newProduct())).doesNotThrowAnyException();
        verify(productRepository).insert(any(Product.class));
    }

    @Test
    @DisplayName("读取信用分抛异常（如迁移未执行）：放行，不误伤正常用户")
    void score_read_failure_fails_open() {
        when(creditRepository.scoreOf(anyLong())).thenThrow(new RuntimeException("Unknown column 'credit_score'"));

        assertThatCode(() -> productService.create(newProduct())).doesNotThrowAnyException();
        verify(productRepository).insert(any(Product.class));
    }
}
