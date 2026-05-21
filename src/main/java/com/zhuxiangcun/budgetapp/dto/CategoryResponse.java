package com.zhuxiangcun.budgetapp.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CategoryResponse {

    private Long id;

    private Long userId;

    private String name;

    private String icon;

    private String color;

    private String type;

    private Integer sortOrder;

    private Boolean isDefault;

    private LocalDateTime createdAt;
}
