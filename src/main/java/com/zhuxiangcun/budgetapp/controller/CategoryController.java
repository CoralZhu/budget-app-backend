package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.CategoryDeleteResponse;
import com.zhuxiangcun.budgetapp.dto.CategoryRequest;
import com.zhuxiangcun.budgetapp.dto.CategoryResponse;
import com.zhuxiangcun.budgetapp.service.CategoryService;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    private final JwtUtil jwtUtil;

    public CategoryController(CategoryService categoryService, JwtUtil jwtUtil) {
        this.categoryService = categoryService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        return ResponseEntity.ok(categoryService.list(userId, type));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request,
            HttpServletRequest servletRequest) {
        Long userId = extractUserId(servletRequest);
        return ResponseEntity.ok(categoryService.create(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CategoryDeleteResponse> delete(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        return ResponseEntity.ok(categoryService.delete(userId, id));
    }

    private Long extractUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未提供有效Token");
        }

        String token = authorization.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
