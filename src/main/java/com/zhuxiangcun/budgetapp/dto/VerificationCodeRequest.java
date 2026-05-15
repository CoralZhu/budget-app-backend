package com.zhuxiangcun.budgetapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerificationCodeRequest {

    @NotBlank
    @Email
    private String email;
}
