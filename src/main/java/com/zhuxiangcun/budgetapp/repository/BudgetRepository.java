package com.zhuxiangcun.budgetapp.repository;

import com.zhuxiangcun.budgetapp.model.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndYearMonth(Long userId, String yearMonth);

    @Query("""
            select b from Budget b
            where b.userId = :userId
              and b.yearMonth = :yearMonth
              and (
                    (:category is null and b.category is null)
                    or b.category = :category
                  )
            """)
    Optional<Budget> findByUserIdAndYearMonthAndCategory(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("category") String category);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);
}
