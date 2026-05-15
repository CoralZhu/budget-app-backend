package com.zhuxiangcun.budgetapp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BudgetResponse {

    private Long id;

    private String yearMonth;

    private String category;

    private BigDecimal amount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
