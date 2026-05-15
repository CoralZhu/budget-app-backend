package com.zhuxiangcun.budgetapp.service;

import com.zhuxiangcun.budgetapp.dto.BudgetRequest;
import com.zhuxiangcun.budgetapp.dto.BudgetResponse;
import com.zhuxiangcun.budgetapp.model.Budget;
import com.zhuxiangcun.budgetapp.repository.BudgetRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public BudgetResponse upsert(Long userId, BudgetRequest request) {
        String category = normalizeCategory(request.getCategory());

        Budget budget = budgetRepository
                .findByUserIdAndYearMonthAndCategory(userId, request.getYearMonth(), category)
                .orElseGet(() -> {
                    Budget newBudget = new Budget();
                    newBudget.setUserId(userId);
                    newBudget.setYearMonth(request.getYearMonth());
                    newBudget.setCategory(category);
                    return newBudget;
                });

        budget.setAmount(request.getAmount());
        return toResponse(budgetRepository.save(budget));
    }

    public List<BudgetResponse> listByMonth(Long userId, String yearMonth) {
        return budgetRepository.findByUserIdAndYearMonth(userId, yearMonth)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void delete(Long userId, Long id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("预算不存在"));

        budgetRepository.delete(budget);
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? null : category;
    }

    private BudgetResponse toResponse(Budget budget) {
        BudgetResponse response = new BudgetResponse();
        response.setId(budget.getId());
        response.setYearMonth(budget.getYearMonth());
        response.setCategory(budget.getCategory());
        response.setAmount(budget.getAmount());
        response.setCreatedAt(budget.getCreatedAt());
        response.setUpdatedAt(budget.getUpdatedAt());
        return response;
    }
}
