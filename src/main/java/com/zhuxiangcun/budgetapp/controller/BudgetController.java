package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.BudgetRequest;
import com.zhuxiangcun.budgetapp.dto.BudgetResponse;
import com.zhuxiangcun.budgetapp.service.BudgetService;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    private final JwtUtil jwtUtil;

    public BudgetController(BudgetService budgetService, JwtUtil jwtUtil) {
        this.budgetService = budgetService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> upsert(
            @Valid @RequestBody BudgetRequest request,
            HttpServletRequest servletRequest) {
        Long userId = extractUserId(servletRequest);
        return ResponseEntity.ok(budgetService.upsert(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> listByMonth(
            @RequestParam String yearMonth,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        return ResponseEntity.ok(budgetService.listByMonth(userId, yearMonth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = extractUserId(request);
        budgetService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未提供有效Token");
        }

        String token = authorization.substring(7);
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token无效或已过期");
        }
    }
}
