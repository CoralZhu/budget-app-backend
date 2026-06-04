package com.zhuxiangcun.budgetapp.service;

import com.zhuxiangcun.budgetapp.dto.LoginRequest;
import com.zhuxiangcun.budgetapp.dto.LoginResponse;
import com.zhuxiangcun.budgetapp.dto.RegisterRequest;
import com.zhuxiangcun.budgetapp.dto.UserResponse;
import com.zhuxiangcun.budgetapp.model.User;
import com.zhuxiangcun.budgetapp.repository.UserRepository;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    private final VerificationCodeService verificationCodeService;

    @Value("${app.skip-email-verification:false}")
    private boolean skipEmailVerification;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            VerificationCodeService verificationCodeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.verificationCodeService = verificationCodeService;
    }

    public UserResponse register(RegisterRequest request) {
        if (!skipEmailVerification) {
            if (!verificationCodeService.verifyCode(request.getEmail(), request.getCode())) {
                throw new RuntimeException("验证码错误或已过期");
            }
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("昵称已被使用");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);
        return toUserResponse(savedUser);
    }

    public LoginResponse login(LoginRequest request) {
        String identifier = request.getAccount().trim();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new RuntimeException("邮箱/昵称或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("邮箱/昵称或密码错误");
        }

        LoginResponse response = new LoginResponse();
        response.setToken(jwtUtil.generateToken(user.getId(), user.getEmail()));
        response.setUser(toUserResponse(user));
        return response;
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setAvatarUrl(user.getAvatarUrl());
        return response;
    }
}
