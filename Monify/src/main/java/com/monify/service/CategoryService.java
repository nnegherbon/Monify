package com.monify.service;

import com.monify.entity.Category;
import com.monify.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Category createCategory(String name, String icon, String color) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Nome da categoria e obrigatorio");
        }
        String normalizedName = name.trim();
        if (categoryRepository.findByName(normalizedName).isPresent()) {
            throw new RuntimeException("Categoria ja cadastrada");
        }
        Category category = Category.builder()
                .name(normalizedName)
                .icon(icon)
                .color(color)
                .build();
        return categoryRepository.save(category);
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public Category getCategoryByName(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
    }

    public void initializeDefaultCategories() {
        ensureDefaultCategory("Alimentação", "🍔", "#FF6B6B");
        ensureDefaultCategory("Transporte", "🚗", "#4ECDC4");
        ensureDefaultCategory("Saúde", "🏥", "#45B7D1");
        ensureDefaultCategory("Lazer", "🎮", "#FFA07A");
        ensureDefaultCategory("Salário", "💰", "#98D8C8");
        ensureDefaultCategory("Educação", "📚", "#6C5CE7");
        ensureDefaultCategory("Utilidades", "💡", "#FDCB6E");
        ensureDefaultCategory("Outros", "📌", "#95A5A6");
        ensureDefaultCategory("Moradia", "🏠", "#2ECC71");
        ensureDefaultCategory("Compras", "🛍️", "#9B59B6");
        ensureDefaultCategory("Investimentos", "📈", "#F1C40F");
        ensureDefaultCategory("Presentes", "🎁", "#E74C3C");
    }

    private void ensureDefaultCategory(String name, String icon, String color) {
        if (categoryRepository.findByName(name).isEmpty()) {
            createCategory(name, icon, color);
        }
    }
}
