package com.zhuxiangcun.budgetapp.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class VerificationCodeService {

    private static final long CODE_EXPIRE_MINUTES = 5;

    private final ConcurrentHashMap<String, VerificationCode> codeCache = new ConcurrentHashMap<>();

    private final JavaMailSender mailSender;

    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCode(String email) {
        String code = generateCode();
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Budget App 注册验证码");
        message.setText("您的注册验证码是：" + code + "，5分钟内有效。");

        mailSender.send(message);
        codeCache.put(email, new VerificationCode(code, expireTime));
    }

    public boolean verifyCode(String email, String code) {
        VerificationCode verificationCode = codeCache.get(email);
        if (verificationCode == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(verificationCode.expireTime())) {
            codeCache.remove(email);
            return false;
        }

        boolean matched = verificationCode.code().equals(code);
        if (matched) {
            codeCache.remove(email);
        }
        return matched;
    }

    private String generateCode() {
        int code = random.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private record VerificationCode(String code, LocalDateTime expireTime) {
    }
}
