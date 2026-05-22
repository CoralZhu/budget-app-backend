package com.zhuxiangcun.budgetapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InsightStats(
        String userNickname,
        String period,
        PeriodStats currentPeriod,
        PeriodStats previousPeriod,
        int changePercent,
        List<CategoryStats> topCategories,
        List<FrequentMerchantStats> frequentMerchants,
        BiggestSingleExpense biggestSingleExpense,
        BigDecimal weekendAvg,
        BigDecimal weekdayAvg,
        IncomeStats income,
        int currentTransactionCount) {

    public record PeriodStats(BigDecimal total, long count, BigDecimal dailyAvg, BigDecimal averagePerExpense) {
    }

    public record CategoryStats(
            String name, BigDecimal current, BigDecimal previous, int changePercent) {
    }

    public record FrequentMerchantStats(
            String name, long count, BigDecimal total, String category) {
    }

    public record BiggestSingleExpense(
            BigDecimal amount, String merchant, String category, LocalDate date) {
    }

    public record IncomeStats(BigDecimal total, long count) {
    }
}
