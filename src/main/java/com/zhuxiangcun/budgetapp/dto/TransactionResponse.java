package com.zhuxiangcun.budgetapp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TransactionResponse {

    private Long id;

    private String type;

    private BigDecimal amount;

    private String category;

    private String merchant;

    private String note;

    private LocalDateTime spentAt;

    private String inputMethod;

    private LocalDateTime createdAt;
}
