package com.zhuxiangcun.budgetapp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReceiptParseResult {

    private String merchant;

    private BigDecimal amount;

    private String category;

    private LocalDateTime spentAt;

    private int confidence;
}
