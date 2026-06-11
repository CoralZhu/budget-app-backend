package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.ImportResult;
import com.zhuxiangcun.budgetapp.service.AlipayCsvImportService;
import com.zhuxiangcun.budgetapp.service.AlipayCsvImportService.InvalidAlipayCsvException;
import com.zhuxiangcun.budgetapp.service.WechatBillImportService;
import com.zhuxiangcun.budgetapp.service.WechatBillImportService.InvalidWechatBillException;
import com.zhuxiangcun.budgetapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final long MAX_IMPORT_SIZE = 10L * 1024L * 1024L;

    private final AlipayCsvImportService alipayCsvImportService;

    private final WechatBillImportService wechatBillImportService;

    private final JwtUtil jwtUtil;

    public ImportController(
            AlipayCsvImportService alipayCsvImportService,
            WechatBillImportService wechatBillImportService,
            JwtUtil jwtUtil) {
        this.alipayCsvImportService = alipayCsvImportService;
        this.wechatBillImportService = wechatBillImportService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/alipay")
    public ResponseEntity<?> importAlipay(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (!isCsv(file)) {
            return ResponseEntity.badRequest().body(AlipayCsvImportService.INVALID_FORMAT_MESSAGE);
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            return ResponseEntity.badRequest().body("文件过大");
        }

        try {
            ImportResult result = alipayCsvImportService.importFile(userId, file);
            return ResponseEntity.ok(result);
        } catch (InvalidAlipayCsvException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/wechat")
    public ResponseEntity<?> importWechat(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (!isWechatBill(file)) {
            return ResponseEntity.badRequest().body(WechatBillImportService.INVALID_FILE_MESSAGE);
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            return ResponseEntity.badRequest().body("文件过大");
        }

        try {
            ImportResult result = wechatBillImportService.importFile(userId, file);
            return ResponseEntity.ok(result);
        } catch (InvalidWechatBillException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleFileTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest().body("文件过大");
    }

    private boolean isCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private boolean isWechatBill(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        String lowerCaseName = filename.toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".xlsx") || lowerCaseName.endsWith(".csv");
    }

    private Long extractUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未提供有效Token");
        }

        String token = authorization.substring(7);
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token无效或已过期");
        }
    }
}
