package com.zhuxiangcun.budgetapp.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuxiangcun.budgetapp.dto.ReceiptParseResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class DeepSeekService {

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final String model;

    public DeepSeekService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.api-url}") String apiUrl,
            @Value("${deepseek.model}") String model) {
        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public ReceiptParseResult parseReceipt(String ocrText) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", buildSystemPrompt()),
                            Map.of("role", "user", "content", ocrText == null ? "" : ocrText)),
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object"));

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("AI 响应为空");
            }

            ReceiptParseResult result = objectMapper.readValue(content, ReceiptParseResult.class);
            if (result.getSpentAt() == null) {
                result.setSpentAt(LocalDateTime.now());
            }
            return result;
        } catch (Exception e) {
            log.error("OCR 调用失败", e);
            throw new RuntimeException("OCR 识别失败: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return "你是一个记账助手,从小票OCR文字里提取消费信息。"
                + "返回严格的JSON,字段:merchant(商家名), "
                + "amount(金额数字), category(分类,只能是这几个之一:餐饮/饮品/交通/购物/教育/娱乐/医疗/其他), "
                + "spentAt(消费时间, ISO格式 yyyy-MM-ddTHH:mm:ss,没有的话用当前时间), "
                + "confidence(0-100的置信度)。"
                + "只输出JSON,不要任何其他文字或markdown标记。";
    }
}
