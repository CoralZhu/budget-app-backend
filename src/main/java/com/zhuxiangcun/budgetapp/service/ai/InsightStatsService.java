package com.zhuxiangcun.budgetapp.service.ai;

import com.zhuxiangcun.budgetapp.dto.InsightStats;
import com.zhuxiangcun.budgetapp.dto.InsightStats.BiggestSingleExpense;
import com.zhuxiangcun.budgetapp.dto.InsightStats.CategoryStats;
import com.zhuxiangcun.budgetapp.dto.InsightStats.FrequentMerchantStats;
import com.zhuxiangcun.budgetapp.dto.InsightStats.IncomeStats;
import com.zhuxiangcun.budgetapp.dto.InsightStats.PeriodStats;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import com.zhuxiangcun.budgetapp.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InsightStatsService {

    private static final int PERIOD_DAYS = 30;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final TransactionRepository transactionRepository;

    private final UserRepository userRepository;

    public InsightStatsService(
            TransactionRepository transactionRepository,
            UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public InsightStats buildStats(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(PERIOD_DAYS);
        LocalDateTime previousStart = now.minusDays(PERIOD_DAYS * 2L);
        List<Transaction> currentTransactions =
                transactionRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
                        userId, currentStart, now);
        List<Transaction> previousTransactions =
                transactionRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
                        userId, previousStart, currentStart);

        List<Transaction> currentExpenses = expenses(currentTransactions);
        List<Transaction> previousExpenses = expenses(previousTransactions);
        BigDecimal currentTotal = total(currentExpenses);
        BigDecimal previousTotal = total(previousExpenses);

        return new InsightStats(
                nickname(userId),
                periodText(currentStart, now),
                periodStats(currentExpenses, currentTotal),
                periodStats(previousExpenses, previousTotal),
                changePercent(currentTotal, previousTotal),
                categoryStats(currentExpenses, previousExpenses),
                frequentMerchantStats(currentExpenses),
                biggestExpense(currentExpenses),
                dayTypeAverage(currentExpenses, currentStart.toLocalDate(), now.toLocalDate(), true),
                dayTypeAverage(currentExpenses, currentStart.toLocalDate(), now.toLocalDate(), false),
                incomeStats(currentTransactions),
                currentTransactions.size());
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElse("");
    }

    private List<Transaction> expenses(List<Transaction> transactions) {
        return transactions.stream()
                .filter(transaction -> "expense".equals(transaction.getType()))
                .toList();
    }

    private PeriodStats periodStats(List<Transaction> expenses, BigDecimal periodTotal) {
        BigDecimal count = BigDecimal.valueOf(expenses.size());
        return new PeriodStats(
                periodTotal,
                expenses.size(),
                divide(periodTotal, BigDecimal.valueOf(PERIOD_DAYS)),
                count.signum() == 0 ? ZERO : divide(periodTotal, count));
    }

    private List<CategoryStats> categoryStats(
            List<Transaction> currentExpenses,
            List<Transaction> previousExpenses) {
        Map<String, BigDecimal> currentTotals = totalsByCategory(currentExpenses);
        Map<String, BigDecimal> previousTotals = totalsByCategory(previousExpenses);
        return currentTotals.entrySet().stream()
                .map(entry -> new CategoryStats(
                        entry.getKey(),
                        money(entry.getValue()),
                        money(previousTotals.getOrDefault(entry.getKey(), BigDecimal.ZERO)),
                        changePercent(entry.getValue(),
                                previousTotals.getOrDefault(entry.getKey(), BigDecimal.ZERO))))
                .sorted(Comparator.comparing(CategoryStats::current).reversed())
                .toList();
    }

    private Map<String, BigDecimal> totalsByCategory(List<Transaction> expenses) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Transaction expense : expenses) {
            totals.merge(blankToDefault(expense.getCategory(), "其他"), amount(expense), BigDecimal::add);
        }
        return totals;
    }

    private List<FrequentMerchantStats> frequentMerchantStats(List<Transaction> expenses) {
        Map<String, MerchantAccumulator> merchants = new HashMap<>();
        for (Transaction expense : expenses) {
            String merchant = blankToNull(expense.getMerchant());
            if (merchant == null) {
                continue;
            }
            merchants.computeIfAbsent(merchant, ignored -> new MerchantAccumulator())
                    .add(expense);
        }

        return merchants.entrySet().stream()
                .filter(entry -> entry.getValue().count >= 3)
                .map(entry -> new FrequentMerchantStats(
                        entry.getKey(),
                        entry.getValue().count,
                        money(entry.getValue().total),
                        entry.getValue().primaryCategory()))
                .sorted(Comparator.comparing(FrequentMerchantStats::total).reversed())
                .toList();
    }

    private BiggestSingleExpense biggestExpense(List<Transaction> expenses) {
        return expenses.stream()
                .max(Comparator.comparing(this::amount))
                .map(expense -> new BiggestSingleExpense(
                        money(amount(expense)),
                        blankToDefault(expense.getMerchant(), ""),
                        blankToDefault(expense.getCategory(), "其他"),
                        expense.getSpentAt().toLocalDate()))
                .orElse(null);
    }

    private BigDecimal dayTypeAverage(
            List<Transaction> expenses, LocalDate start, LocalDate end, boolean weekend) {
        Map<LocalDate, BigDecimal> dailyTotals = new HashMap<>();
        for (Transaction expense : expenses) {
            dailyTotals.merge(expense.getSpentAt().toLocalDate(), amount(expense), BigDecimal::add);
        }

        int dayCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (long offset = 0; offset <= ChronoUnit.DAYS.between(start, end); offset++) {
            LocalDate day = start.plusDays(offset);
            if (isWeekend(day) == weekend) {
                total = total.add(dailyTotals.getOrDefault(day, BigDecimal.ZERO));
                dayCount++;
            }
        }
        return dayCount == 0 ? ZERO : divide(total, BigDecimal.valueOf(dayCount));
    }

    private IncomeStats incomeStats(List<Transaction> transactions) {
        List<Transaction> incomes = transactions.stream()
                .filter(transaction -> "income".equals(transaction.getType()))
                .toList();
        return new IncomeStats(total(incomes), incomes.size());
    }

    private BigDecimal total(List<Transaction> transactions) {
        return money(transactions.stream()
                .map(this::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal amount(Transaction transaction) {
        return transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal value, BigDecimal divisor) {
        return value.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private int changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() == 0 ? 0 : 100;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String periodText(LocalDateTime start, LocalDateTime end) {
        return start.toLocalDate() + " 到 " + end.toLocalDate();
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static class MerchantAccumulator {

        long count;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, Long> categories = new HashMap<>();

        void add(Transaction expense) {
            count++;
            total = total.add(expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount());
            String category = expense.getCategory() == null || expense.getCategory().isBlank()
                    ? "其他"
                    : expense.getCategory().trim();
            categories.merge(category, 1L, Long::sum);
        }

        String primaryCategory() {
            return categories.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("其他");
        }
    }
}
