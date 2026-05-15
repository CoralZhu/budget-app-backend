package com.zhuxiangcun.budgetapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BudgetRequest {

    @NotBlank
    private String yearMonth;

    private String category;

    @NotNull
    @Positive
    private BigDecimal amount;
}
