package com.zhuxiangcun.budgetapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryDeleteResponse {

    private Long id;

    private String name;

    private long affectedTransactions;
}
