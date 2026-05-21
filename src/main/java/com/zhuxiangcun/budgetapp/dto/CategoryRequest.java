package com.zhuxiangcun.budgetapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank
    @Size(max = 20)
    private String name;

    @Size(max = 10)
    private String icon;

    @Size(max = 20)
    private String color;

    @NotBlank
    @Size(max = 20)
    private String type;
}
