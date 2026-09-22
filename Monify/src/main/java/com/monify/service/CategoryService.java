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
        // Receitas do hotel (RF001/RF002 - reservas, eventos e serviços adicionais)
        ensureDefaultCategory("Reservas", "🛏️", "#1F9E6D");
        ensureDefaultCategory("Eventos", "🎉", "#D9A65C");
        ensureDefaultCategory("Serviços Adicionais", "🛎️", "#4FB3BF");

        // Despesas do hotel (RF003/RF004 - manutenção, insumos, folha de pagamento e encargos)
        ensureDefaultCategory("Manutenção", "🛠️", "#8C6D46");
        ensureDefaultCategory("Insumos", "📦", "#B8863B");
        ensureDefaultCategory("Folha de Pagamento", "👥", "#5D6D7E");
        ensureDefaultCategory("Encargos", "🧾", "#C0392B");
        ensureDefaultCategory("Utilidades", "💡", "#E1B12C");
        ensureDefaultCategory("Outros", "📌", "#95A5A6");
    }

    private void ensureDefaultCategory(String name, String icon, String color) {
        if (categoryRepository.findByName(name).isEmpty()) {
            createCategory(name, icon, color);
        }
    }
}
