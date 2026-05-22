package com.zhuxiangcun.budgetapp.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InsightResult(
        LocalDateTime generatedAt,
        String period,
        List<Insight> insights) {

    public record Insight(String type, String title, String content, String emoji) {
    }
}
