package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.InsightResult;
import com.zhuxiangcun.budgetapp.service.ai.InsightsService;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class InsightsController {

    private final InsightsService insightsService;

    private final JwtUtil jwtUtil;

    public InsightsController(InsightsService insightsService, JwtUtil jwtUtil) {
        this.insightsService = insightsService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/insights")
    public ResponseEntity<InsightResult> getInsights(
            @RequestParam(defaultValue = "false") boolean force,
            HttpServletRequest request) {
        return ResponseEntity.ok(insightsService.getInsights(extractUserId(request), force));
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
