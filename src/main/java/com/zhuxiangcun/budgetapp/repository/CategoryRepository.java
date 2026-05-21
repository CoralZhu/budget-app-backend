package com.zhuxiangcun.budgetapp.repository;

import com.zhuxiangcun.budgetapp.model.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdIsNullOrUserId(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    Optional<Category> findByUserIdAndNameAndType(Long userId, String name, String type);

    boolean existsByUserIdIsNullAndNameAndType(String name, String type);
}
