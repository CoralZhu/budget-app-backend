package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.TransactionRequest;
import com.zhuxiangcun.budgetapp.dto.TransactionResponse;
import com.zhuxiangcun.budgetapp.service.TransactionService;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    private final JwtUtil jwtUtil;

    public TransactionController(TransactionService transactionService, JwtUtil jwtUtil) {
        this.transactionService = transactionService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request,
            HttpServletRequest servletRequest) {
        Long userId = extractUserId(servletRequest);
        return ResponseEntity.ok(transactionService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(HttpServletRequest request) {
        Long userId = extractUserId(request);
        return ResponseEntity.ok(transactionService.list(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request,
            HttpServletRequest servletRequest) {
        Long userId = extractUserId(servletRequest);
        return ResponseEntity.ok(transactionService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = extractUserId(request);
        transactionService.delete(userId, id);
        return ResponseEntity.noContent().build();
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
