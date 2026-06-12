package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.LoginRequest;
import com.zhuxiangcun.budgetapp.dto.LoginResponse;
import com.zhuxiangcun.budgetapp.dto.RegisterRequest;
import com.zhuxiangcun.budgetapp.dto.UserResponse;
import com.zhuxiangcun.budgetapp.dto.VerificationCodeRequest;
import com.zhuxiangcun.budgetapp.service.UserService;
import com.zhuxiangcun.budgetapp.service.VerificationCodeService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    private final VerificationCodeService verificationCodeService;

    public AuthController(UserService userService, VerificationCodeService verificationCodeService) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
    }

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, String>> sendCode(@Valid @RequestBody VerificationCodeRequest request) {
        verificationCodeService.sendCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "验证码已发送"));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/demo")
    public ResponseEntity<LoginResponse> demo() {
        return ResponseEntity.ok(userService.demoLogin());
    }
}
