package com.zhuxiangcun.budgetapp.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhuxiangcun.budgetapp.dto.InsightResult;
import com.zhuxiangcun.budgetapp.dto.InsightStats;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsightsServiceTests {

    private final InsightStatsService insightStatsService =
            org.mockito.Mockito.mock(InsightStatsService.class);

    private final DeepSeekService deepSeekService = org.mockito.Mockito.mock(DeepSeekService.class);

    private final InsightsService service = new InsightsService(insightStatsService, deepSeekService);

    @Test
    void cachesGeneratedInsightsUntilForceRefresh() {
        InsightStats stats = stats(3);
        when(insightStatsService.buildStats(8L)).thenReturn(stats);
        when(deepSeekService.generateInsights(stats))
                .thenReturn(List.of(new InsightResult.Insight(
                        "expense_trend", "支出上扬", "购物这段时间更活跃。", "📈")));

        InsightResult first = service.getInsights(8L, false);
        InsightResult second = service.getInsights(8L, false);
        InsightResult refreshed = service.getInsights(8L, true);

        assertThat(first).isSameAs(second);
        assertThat(refreshed.insights()).hasSize(1);
        verify(insightStatsService, times(2)).buildStats(8L);
        verify(deepSeekService, times(2)).generateInsights(stats);
    }

    @Test
    void returnsFallbackWhenRecentDataIsSparseOrAiFails() {
        when(insightStatsService.buildStats(1L)).thenReturn(stats(0));
        when(insightStatsService.buildStats(2L)).thenReturn(stats(2));
        InsightStats normalStats = stats(4);
        when(insightStatsService.buildStats(3L)).thenReturn(normalStats);
        when(deepSeekService.generateInsights(normalStats)).thenThrow(new RuntimeException("timeout"));

        assertThat(service.getInsights(1L, false).insights()).singleElement()
                .satisfies(insight -> assertThat(insight.content()).contains("快记几笔"));
        assertThat(service.getInsights(2L, false).insights()).singleElement()
                .satisfies(insight -> assertThat(insight.content()).contains("不足3笔"));
        assertThat(service.getInsights(3L, false).insights()).singleElement()
                .satisfies(insight -> assertThat(insight.content()).contains("暂时不可用"));
    }

    private InsightStats stats(int currentTransactionCount) {
        InsightStats.PeriodStats current =
                new InsightStats.PeriodStats(BigDecimal.TEN, currentTransactionCount, BigDecimal.ZERO, BigDecimal.ONE);
        InsightStats.PeriodStats previous =
                new InsightStats.PeriodStats(BigDecimal.ONE, 1, BigDecimal.ZERO, BigDecimal.ONE);
        return new InsightStats(
                "小村",
                "2026-04-22 到 2026-05-22",
                current,
                previous,
                0,
                List.of(),
                List.of(),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new InsightStats.IncomeStats(BigDecimal.ZERO, 0),
                currentTransactionCount);
    }
}
