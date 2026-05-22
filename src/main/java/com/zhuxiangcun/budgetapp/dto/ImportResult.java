package com.zhuxiangcun.budgetapp.dto;

import java.util.List;

public record ImportResult(
        int totalRows,
        int imported,
        int skippedNotExpense,
        int skippedFailedStatus,
        int skippedDuplicate,
        int failed,
        List<FailedSample> failedSamples) {

    public record FailedSample(int row, String reason) {
    }
}
