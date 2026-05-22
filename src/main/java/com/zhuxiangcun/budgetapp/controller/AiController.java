package com.zhuxiangcun.budgetapp.controller;

import com.zhuxiangcun.budgetapp.dto.ReceiptParseResult;
import com.zhuxiangcun.budgetapp.dto.VoiceParseRequest;
import com.zhuxiangcun.budgetapp.service.ai.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final DeepSeekService deepSeekService;

    public AiController(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    @PostMapping("/parse-voice")
    public ResponseEntity<?> parseVoice(@RequestBody VoiceParseRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body("语音文本不能为空");
        }

        try {
            ReceiptParseResult result = deepSeekService.parseVoiceText(request.getText());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("语音文本解析失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("语音文本解析失败");
        }
    }
}
