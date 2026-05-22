package com.zhuxiangcun.budgetapp.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuxiangcun.budgetapp.dto.InsightResult;
import com.zhuxiangcun.budgetapp.dto.InsightStats;
import com.zhuxiangcun.budgetapp.dto.ReceiptParseResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class DeepSeekService {

    private static final DateTimeFormatter PROMPT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINESE);

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
            return parseText(buildReceiptPrompt(currentPromptTime()), ocrText);
        } catch (Exception e) {
            log.error("OCR 调用失败", e);
            throw new RuntimeException("OCR 识别失败: " + e.getMessage(), e);
        }
    }

    public ReceiptParseResult parseVoiceText(String text) {
        try {
            return parseText(buildVoicePrompt(currentPromptTime()), text);
        } catch (Exception e) {
            log.error("语音文本解析失败", e);
            throw new RuntimeException("语音文本解析失败: " + e.getMessage(), e);
        }
    }

    public List<InsightResult.Insight> generateInsights(InsightStats stats) {
        try {
            String userPrompt = "我的昵称是 " + safeText(stats.userNickname()) + "。这是我过去 "
                    + stats.period() + " 的消费数据,请给我 3-5 条洞察。\n"
                    + objectMapper.writeValueAsString(stats);
            String content = chatJson(buildInsightsPrompt(), userPrompt, 0.4);
            GeneratedInsights generatedInsights =
                    objectMapper.readValue(content, GeneratedInsights.class);
            if (generatedInsights.insights() == null || generatedInsights.insights().isEmpty()) {
                throw new RuntimeException("AI 洞察为空");
            }
            return generatedInsights.insights();
        } catch (Exception e) {
            log.error("AI 洞察生成失败", e);
            throw new RuntimeException("AI 洞察生成失败: " + e.getMessage(), e);
        }
    }

    private ReceiptParseResult parseText(String systemPrompt, String userText) throws Exception {
        String content = chatJson(systemPrompt, userText, 0.1);
        ReceiptParseResult result = objectMapper.readValue(content, ReceiptParseResult.class);
        if (result.getSpentAt() == null) {
            result.setSpentAt(LocalDateTime.now());
        }
        return result;
    }

    private String chatJson(String systemPrompt, String userText, double temperature)
            throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt),
                        Map.of("role", "user", "content", userText == null ? "" : userText)),
                "temperature", temperature,
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
        return content;
    }

    private String buildReceiptPrompt(String now) {
        return "你是一个记账助手,从小票OCR文字里提取消费信息。"
                + "返回严格的JSON,字段:merchant(商家名), "
                + "amount(金额数字), category(分类,只能是这几个之一:餐饮/饮品/交通/购物/教育/娱乐/医疗/其他), "
                + "spentAt(消费时间, ISO格式 yyyy-MM-ddTHH:mm:ss,没有的话用当前时间), "
                + "confidence(0-100的置信度)。"
                + buildRelativeTimePrompt(now)
                + "只输出JSON,不要任何其他文字或markdown标记。";
    }

    private String buildVoicePrompt(String now) {
        return "你是一个记账助手,从用户的口语描述里提取消费信息。"
                + "返回严格的JSON,字段:"
                + "merchant(商家名,从话里听出来,例如'瑞幸'/'麦当劳';如果没说就空字符串), "
                + "amount(金额数字), "
                + "category(只能是这些之一:餐饮/饮品/交通/购物/教育/娱乐/医疗/其他), "
                + "spentAt(消费时间, ISO格式 yyyy-MM-ddTHH:mm:ss;"
                + "'今天中午'→当前日期12:00,'昨天晚上'→昨天19:00,以此类推;没说时间就用当前时间), "
                + "confidence(0-100的置信度)。"
                + buildRelativeTimePrompt(now)
                + "只输出JSON,不要任何其他文字或markdown标记。";
    }

    private String currentPromptTime() {
        return LocalDateTime.now().format(PROMPT_TIME_FORMATTER);
    }

    private String buildRelativeTimePrompt(String now) {
        return "当前时间是 " + now + "。"
                + "理解口语或缺失日期里的相对时间时,以这个时间为基准:"
                + "'今天'/'刚才'/'现在'→当前日期;"
                + "'今天中午'→当前日期12:00;"
                + "'今天晚上'→当前日期19:00;"
                + "'昨天'→当前日期-1;"
                + "'前天'→当前日期-2;"
                + "'上周X'→上一个星期X。"
                + "没说时间的话,默认当前时间。";
    }

    private String buildInsightsPrompt() {
        return """
                你是一个友好的记账 AI 助手,根据用户的消费统计数据生成 3-5 条个性化洞察。
                风格要求:
                - 像朋友说话,口语化,有人味,不要"客户"、"用户"这种距离感词
                - 每条洞察 2-3 句话,先讲事实,再给建议或调侃
                - 不要说"恭喜你"、"加油哦"这种空话
                - 偶尔可以幽默,但不要油腻
                - 用具体数字,不要"较多"、"较少"这种模糊词
                - 如果某个分类突然涨很多,要点出来
                - 如果高频去同一家(瑞幸/麦当劳类),可以调侃或给替代建议
                - 如果周末花得多,建议把握周末
                - 如果没什么洞察可说(数据太少),可以减少到 1-2 条

                输出严格 JSON,不要任何其他文字或 markdown:
                {
                  "insights": [
                    { "type": "...", "title": "...", "content": "...", "emoji": "..." }
                  ]
                }

                字段说明:
                - type: 取值之一 expense_trend / category_focus / saving_tip / frequent_merchant / single_big / weekend_pattern / general
                - title: 5-10 个字,简短有趣
                - content: 50-100 字
                - emoji: 一个 emoji,跟内容相关
                """;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "我" : value;
    }

    private record GeneratedInsights(List<InsightResult.Insight> insights) {
    }
}
