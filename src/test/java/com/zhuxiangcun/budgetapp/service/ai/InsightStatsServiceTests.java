package com.zhuxiangcun.budgetapp.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zhuxiangcun.budgetapp.dto.InsightStats;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.model.User;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import com.zhuxiangcun.budgetapp.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InsightStatsServiceTests {

    private final TransactionRepository transactionRepository =
            org.mockito.Mockito.mock(TransactionRepository.class);

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);

    private final InsightStatsService service =
            new InsightStatsService(transactionRepository, userRepository);

    @Test
    void aggregatesExpenseWindowsMerchantsAndIncomeWithoutRawRows() {
        LocalDateTime now = LocalDateTime.now();
        when(transactionRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        transaction("expense", "10.00", "饮品", "瑞幸", now.minusDays(1)),
                        transaction("expense", "15.00", "饮品", "瑞幸", now.minusDays(2)),
                        transaction("expense", "20.00", "饮品", "瑞幸", now.minusDays(3)),
                        transaction("expense", "80.00", "购物", "屈臣氏", now.minusDays(4)),
                        transaction("income", "500.00", "工资", "公司", now.minusDays(5))))
                .thenReturn(List.of(
                        transaction("expense", "30.00", "饮品", "瑞幸", now.minusDays(35)),
                        transaction("expense", "20.00", "餐饮", "面馆", now.minusDays(36))));
        User user = new User();
        user.setUsername("小村");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        InsightStats stats = service.buildStats(1L);

        assertThat(stats.userNickname()).isEqualTo("小村");
        assertThat(stats.currentPeriod().total()).isEqualByComparingTo("125.00");
        assertThat(stats.currentPeriod().count()).isEqualTo(4);
        assertThat(stats.previousPeriod().total()).isEqualByComparingTo("50.00");
        assertThat(stats.changePercent()).isEqualTo(150);
        assertThat(stats.topCategories()).first()
                .satisfies(category -> {
                    assertThat(category.name()).isEqualTo("购物");
                    assertThat(category.current()).isEqualByComparingTo("80.00");
                });
        assertThat(stats.frequentMerchants()).singleElement()
                .satisfies(merchant -> {
                    assertThat(merchant.name()).isEqualTo("瑞幸");
                    assertThat(merchant.count()).isEqualTo(3);
                    assertThat(merchant.total()).isEqualByComparingTo("45.00");
                    assertThat(merchant.category()).isEqualTo("饮品");
                });
        assertThat(stats.biggestSingleExpense().merchant()).isEqualTo("屈臣氏");
        assertThat(stats.income().total()).isEqualByComparingTo("500.00");
        assertThat(stats.income().count()).isEqualTo(1);
        assertThat(stats.currentTransactionCount()).isEqualTo(5);
    }

    private Transaction transaction(
            String type, String amount, String category, String merchant, LocalDateTime spentAt) {
        Transaction transaction = new Transaction();
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCategory(category);
        transaction.setMerchant(merchant);
        transaction.setSpentAt(spentAt);
        return transaction;
    }
}
