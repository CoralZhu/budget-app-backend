package com.zhuxiangcun.budgetapp.repository;

import com.zhuxiangcun.budgetapp.model.Transaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderBySpentAtDesc(Long userId);

    List<Transaction> findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndCategory(Long userId, String category);

    @Modifying
    @Query("""
            update Transaction t
            set t.category = :newCategory
            where t.userId = :userId and t.category = :oldCategory
            """)
    int updateCategoryByUserIdAndCategory(
            @Param("userId") Long userId,
            @Param("oldCategory") String oldCategory,
            @Param("newCategory") String newCategory);
}
