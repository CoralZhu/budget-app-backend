package com.zhuxiangcun.budgetapp.service.ai;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralRequest;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AliyunOcrService {

    private final Client client;

    private final ObjectMapper objectMapper;

    public AliyunOcrService(
            @Value("${aliyun.access-key-id}") String accessKeyId,
            @Value("${aliyun.access-key-secret}") String accessKeySecret,
            @Value("${aliyun.ocr.region}") String region,
            ObjectMapper objectMapper) throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "ocr-api." + region + ".aliyuncs.com";
        this.client = new Client(config);
        this.objectMapper = objectMapper;
    }

    public String recognizeText(byte[] imageBytes) {
        try {
            RecognizeGeneralRequest request = new RecognizeGeneralRequest()
                    .setBody(new ByteArrayInputStream(imageBytes));
            RecognizeGeneralResponse response = client.recognizeGeneral(request);
            String data = response.getBody().getData();
            return extractText(data);
        } catch (Exception e) {
            log.error("OCR 调用失败", e);
            throw new RuntimeException("OCR 识别失败: " + e.getMessage(), e);
        }
    }

    private String extractText(String data) throws Exception {
        if (data == null || data.isBlank()) {
            return "";
        }

        JsonNode root = objectMapper.readTree(data);
        List<String> lines = new ArrayList<>();
        collectText(root, lines);
        return String.join("\n", lines);
    }

    private void collectText(JsonNode node, List<String> lines) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            addIfPresent(node, lines, "content");
            addIfPresent(node, lines, "text");
            addIfPresent(node, lines, "word");
            addIfPresent(node, lines, "words");

            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), lines));
            return;
        }

        if (node.isArray()) {
            node.forEach(child -> collectText(child, lines));
        }
    }

    private void addIfPresent(JsonNode node, List<String> lines, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            String text = value.asText().trim();
            if (!lines.contains(text)) {
                lines.add(text);
            }
        }
    }
}
