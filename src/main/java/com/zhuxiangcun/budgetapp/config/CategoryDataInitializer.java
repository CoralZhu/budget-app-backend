package com.zhuxiangcun.budgetapp.config;

import com.zhuxiangcun.budgetapp.model.Category;
import com.zhuxiangcun.budgetapp.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CategoryDataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    public CategoryDataInitializer(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) {
        seed("餐饮", "🍴", "#6E73F2", "expense", 1);
        seed("饮品", "☕", "#8B7DF7", "expense", 2);
        seed("交通", "🚇", "#7C3AED", "expense", 3);
        seed("购物", "🛍️", "#A78BFA", "expense", 4);
        seed("教育", "📚", "#6366F1", "expense", 5);
        seed("娱乐", "🎮", "#818CF8", "expense", 6);
        seed("医疗", "💊", "#14B8A6", "expense", 7);
        seed("其他", "📌", "#94A3B8", "expense", 99);

        seed("工资", "💰", "#10B981", "income", 1);
        seed("奖金", "🏆", "#22C55E", "income", 2);
        seed("红包", "🧧", "#EF4444", "income", 3);
        seed("其他", "🎁", "#94A3B8", "income", 99);
    }

    private void seed(String name, String icon, String color, String type, int sortOrder) {
        if (categoryRepository.existsByUserIdIsNullAndNameAndType(name, type)) {
            return;
        }

        Category category = new Category();
        category.setUserId(null);
        category.setName(name);
        category.setIcon(icon);
        category.setColor(color);
        category.setType(type);
        category.setSortOrder(sortOrder);
        category.setIsDefault(true);
        categoryRepository.save(category);
    }
}
