package com.zhuxiangcun.budgetapp.repository;

import com.zhuxiangcun.budgetapp.model.Transaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderBySpentAtDesc(Long userId);

    List<Transaction> findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);
}
