package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Category;
import com.example.campussecondhand.repository.CategoryRepository;
import com.example.campussecondhand.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类管理修复后的契约。
 *
 * <p>修复前的三个真实问题：按中文 name 统计商品数永远为 0；
 * 标识与商品脱节；删除分类不校验是否还有商品在用。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCategoryContractTest {

    private static final String AUTH = "Bearer admin-token";

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AdminController controller;

    private int codeOf(ResponseEntity<ApiResponse<?>> response) {
        return response.getBody() == null ? -1 : response.getBody().getCode();
    }

    private Category category(Long id, String name, String code) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setCode(code);
        c.setStatus(1);
        c.setSortOrder(1);
        return c;
    }

    @Test
    @DisplayName("分类列表按 code 统计商品数（修复按中文名统计恒为 0）")
    void list_counts_products_by_code() {
        when(categoryRepository.selectList(any())).thenReturn(List.of(category(1L, "图书教材", "books")));
        when(productRepository.selectCount(any())).thenReturn(17L);

        ResponseEntity<ApiResponse<?>> response = controller.getAllCategories(AUTH);

        assertThat(codeOf(response)).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody().getData();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("code")).isEqualTo("books");
        assertThat(rows.get(0).get("productCount")).isEqualTo(17L);
    }

    @Test
    @DisplayName("新增分类：标识格式非法/重复都被拒，合法则落库")
    void add_category_validates_code() {
        // 中文或大写标识不允许
        assertThat(codeOf(controller.addCategory(AUTH,
                Map.of("name", "数码配件", "code", "数码")))).isEqualTo(400);
        // 首字符必须是字母
        assertThat(codeOf(controller.addCategory(AUTH,
                Map.of("name", "数码配件", "code", "1abc")))).isEqualTo(400);

        when(categoryRepository.selectCount(any())).thenReturn(1L);
        assertThat(codeOf(controller.addCategory(AUTH,
                Map.of("name", "数码配件", "code", "gadgets")))).isEqualTo(400);

        when(categoryRepository.selectCount(any())).thenReturn(0L);
        assertThat(codeOf(controller.addCategory(AUTH,
                Map.of("name", "数码配件", "code", "gadgets")))).isEqualTo(200);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("gadgets");
    }

    @Test
    @DisplayName("分类标识创建后不可修改")
    void update_keeps_code_immutable() {
        when(categoryRepository.selectById(1L)).thenReturn(category(1L, "图书教材", "books"));

        assertThat(codeOf(controller.updateCategory(AUTH, 1L,
                Map.of("code", "book2")))).isEqualTo(400);

        verify(categoryRepository, never()).updateById(any(Category.class));
    }

    @Test
    @DisplayName("改名不影响商品归属，可正常保存")
    void update_allows_rename() {
        when(categoryRepository.selectById(1L)).thenReturn(category(1L, "图书教材", "books"));

        ResponseEntity<ApiResponse<?>> response = controller.updateCategory(AUTH, 1L,
                Map.of("name", "教材与图书", "status", 1, "sortOrder", 3));

        assertThat(codeOf(response)).isEqualTo(200);
        Category saved = (Category) response.getBody().getData();
        assertThat(saved.getName()).isEqualTo("教材与图书");
        assertThat(saved.getCode()).isEqualTo("books");
    }

    @Test
    @DisplayName("分类下仍有商品时拒绝删除；空分类可删")
    void delete_blocks_when_products_in_use() {
        when(categoryRepository.selectById(1L)).thenReturn(category(1L, "图书教材", "books"));
        when(productRepository.selectCount(any())).thenReturn(17L);

        ResponseEntity<ApiResponse<?>> blocked = controller.deleteCategory(AUTH, 1L);
        assertThat(codeOf(blocked)).isEqualTo(400);
        assertThat(blocked.getBody().getMessage()).contains("17");
        verify(categoryRepository, never()).deleteById(1L);

        when(productRepository.selectCount(any())).thenReturn(0L);
        assertThat(codeOf(controller.deleteCategory(AUTH, 1L))).isEqualTo(200);
        verify(categoryRepository).deleteById(1L);
    }
}
