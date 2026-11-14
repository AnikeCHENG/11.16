package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Category;
import com.example.campussecondhand.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类只读接口。
 *
 * <p>写操作（新增/修改/删除）统一走 {@code /api/admin/categories}，
 * 由 {@code AdminAuthInterceptor} 校验管理员身份。此处曾提供 C 端写接口，
 * 只要任何一个登录用户持有 token 就能改分类，属于越权面，已移除。</p>
 */
@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllCategories() {
        List<Category> categories = categoryRepository.selectList(null);
        return ResponseEntity.ok(ApiResponse.success("获取成功", categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getCategoryById(@PathVariable Long id) {
        Category category = categoryRepository.selectById(id);
        if (category != null) {
            return ResponseEntity.ok(ApiResponse.success("获取成功", category));
        }
        return ResponseEntity.ok(ApiResponse.error(404, "分类不存在"));
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<ApiResponse<?>> getCategoriesByParentId(@PathVariable Long parentId) {
        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", parentId);
        List<Category> categories = categoryRepository.selectList(wrapper);
        return ResponseEntity.ok(ApiResponse.success("获取成功", categories));
    }
}