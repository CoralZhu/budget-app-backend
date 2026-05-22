package com.zhuxiangcun.budgetapp.service.ai;

import com.zhuxiangcun.budgetapp.dto.InsightResult;
import com.zhuxiangcun.budgetapp.dto.InsightStats;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class InsightsService {

    private static final String RESPONSE_PERIOD = "最近30天";

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final InsightStatsService insightStatsService;

    private final DeepSeekService deepSeekService;

    private final ConcurrentHashMap<Long, CachedInsights> cache = new ConcurrentHashMap<>();

    public InsightsService(InsightStatsService insightStatsService, DeepSeekService deepSeekService) {
        this.insightStatsService = insightStatsService;
        this.deepSeekService = deepSeekService;
    }

    public InsightResult getInsights(Long userId, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        CachedInsights cached = cache.get(userId);
        if (!force && cached != null && Duration.between(cached.generatedAt(), now).compareTo(CACHE_TTL) < 0) {
            return cached.result();
        }

        InsightStats stats = insightStatsService.buildStats(userId);
        InsightResult result = generateResult(stats, now);
        cache.put(userId, new CachedInsights(result.generatedAt(), result));
        return result;
    }

    private InsightResult generateResult(InsightStats stats, LocalDateTime generatedAt) {
        if (stats.currentTransactionCount() == 0) {
            return fallback(
                    generatedAt,
                    "还没开始记账",
                    "还没开始记账?快记几笔我才能给你建议。",
                    "🌱");
        }
        if (stats.currentTransactionCount() < 3) {
            return fallback(
                    generatedAt,
                    "数据还不够",
                    "最近30天的数据还不足3笔,先多记几笔,我再帮你看消费习惯。",
                    "🌱");
        }

        try {
            return new InsightResult(generatedAt, RESPONSE_PERIOD, deepSeekService.generateInsights(stats));
        } catch (RuntimeException e) {
            return fallback(
                    generatedAt,
                    "洞察稍后再来",
                    "AI 洞察暂时不可用,稍后再试。",
                    "💡");
        }
    }

    private InsightResult fallback(
            LocalDateTime generatedAt, String title, String content, String emoji) {
        return new InsightResult(
                generatedAt,
                RESPONSE_PERIOD,
                List.of(new InsightResult.Insight("general", title, content, emoji)));
    }

    private record CachedInsights(LocalDateTime generatedAt, InsightResult result) {
    }
}
