package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.ReceiptParseResult;
import com.zhuxiangcun.budgetapp.service.ai.AliyunOcrService;
import com.zhuxiangcun.budgetapp.service.ai.DeepSeekService;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final AliyunOcrService aliyunOcrService;

    private final DeepSeekService deepSeekService;

    private final JwtUtil jwtUtil;

    public OcrController(
            AliyunOcrService aliyunOcrService,
            DeepSeekService deepSeekService,
            JwtUtil jwtUtil) {
        this.aliyunOcrService = aliyunOcrService;
        this.deepSeekService = deepSeekService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/receipt")
    public ResponseEntity<?> recognizeReceipt(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        extractUserId(request);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("请上传图片");
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            return ResponseEntity.badRequest().body("图片过大");
        }

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException e) {
            log.error("图片读取失败", e);
            return ResponseEntity.badRequest().body("图片读取失败");
        }

        String ocrText;
        try {
            ocrText = aliyunOcrService.recognizeText(imageBytes);
        } catch (RuntimeException e) {
            log.error("OCR 识别失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("OCR 识别失败");
        }

        try {
            ReceiptParseResult result = deepSeekService.parseReceipt(ocrText);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("AI 解析失败,请手动记账", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("AI 解析失败,请手动记账");
        }
    }

    private Long extractUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未提供有效Token");
        }

        String token = authorization.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
