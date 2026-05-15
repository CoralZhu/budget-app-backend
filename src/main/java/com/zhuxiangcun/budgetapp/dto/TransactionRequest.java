package com.zhuxiangcun.budgetapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TransactionRequest {

    @NotBlank
    private String type;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String category;

    private String merchant;

    private String note;

    @NotNull
    private LocalDateTime spentAt;

    private String inputMethod = "manual";
}
