package com.zhuxiangcun.budgetapp.service;

import com.zhuxiangcun.budgetapp.dto.CategoryDeleteResponse;
import com.zhuxiangcun.budgetapp.dto.CategoryRequest;
import com.zhuxiangcun.budgetapp.dto.CategoryResponse;
import com.zhuxiangcun.budgetapp.model.Category;
import com.zhuxiangcun.budgetapp.repository.CategoryRepository;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private final TransactionRepository transactionRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<CategoryResponse> list(Long userId, String type) {
        return categoryRepository.findByUserIdIsNullOrUserId(userId)
                .stream()
                .filter(category -> type == null || type.isBlank() || type.equals(category.getType()))
                .sorted(Comparator
                        .comparing(Category::getType)
                        .thenComparing(Category::getSortOrder)
                        .thenComparing(Category::getId))
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse create(Long userId, CategoryRequest request) {
        String name = request.getName().trim();
        String type = normalizeType(request.getType());

        categoryRepository.findByUserIdAndNameAndType(userId, name, type)
                .ifPresent(category -> {
                    throw new RuntimeException("同名分类已存在");
                });

        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        category.setIcon(blankToNull(request.getIcon()));
        category.setColor(blankToNull(request.getColor()));
        category.setType(type);
        category.setSortOrder(100);
        category.setIsDefault(false);

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDeleteResponse delete(Long userId, Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在"));

        if (category.getUserId() == null || Boolean.TRUE.equals(category.getIsDefault())) {
            throw new RuntimeException("默认分类不可删除");
        }

        if (!category.getUserId().equals(userId)) {
            throw new RuntimeException("分类不存在");
        }

        long count = transactionRepository.countByUserIdAndCategory(userId, category.getName());
        if (count > 0) {
            transactionRepository.updateCategoryByUserIdAndCategory(userId, category.getName(), "其他");
        }

        categoryRepository.delete(category);
        return new CategoryDeleteResponse(id, category.getName(), count);
    }

    private String normalizeType(String type) {
        if (!"expense".equals(type) && !"income".equals(type)) {
            throw new RuntimeException("分类类型不正确");
        }
        return type;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setUserId(category.getUserId());
        response.setName(category.getName());
        response.setIcon(category.getIcon());
        response.setColor(category.getColor());
        response.setType(category.getType());
        response.setSortOrder(category.getSortOrder());
        response.setIsDefault(category.getIsDefault());
        response.setCreatedAt(category.getCreatedAt());
        return response;
    }
}
