package com.monify.service;

import com.monify.dto.SpendingLimitDTO;
import com.monify.entity.Category;
import com.monify.entity.SpendingLimit;
import com.monify.entity.User;
import com.monify.repository.SpendingLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SpendingLimitService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final String GENERAL_CATEGORY_KEY = "geral";
    private static final String GENERAL_CATEGORY_NAME = "Geral (mês todo)";

    private final SpendingLimitRepository spendingLimitRepository;
    private final UserService userService;
    private final CategoryService categoryService;

    public List<SpendingLimitDTO> getLimits(Long userId, String month, SpendingLimit.LimitType type) {
        userService.getUserById(userId);
        List<SpendingLimit> limits;
        if (month != null && !month.isBlank() && type != null) {
            limits = spendingLimitRepository.findByUserIdAndMonthAndLimitTypeOrderByCategoryNameAsc(userId, month, type);
        } else if (month != null && !month.isBlank()) {
            limits = spendingLimitRepository.findByUserIdAndMonthOrderByCategoryNameAsc(userId, month);
        } else {
            limits = spendingLimitRepository.findByUserIdOrderByCategoryNameAsc(userId);
        }

        return limits.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SpendingLimitDTO createLimit(Long userId, SpendingLimitDTO dto) {
        User user = userService.getUserById(userId);
        SpendingLimit.LimitType limitType = dto.getLimitType() != null
                ? dto.getLimitType()
                : SpendingLimit.LimitType.EXPENSE;
        SpendingLimit.Scope scope = dto.getScope() != null ? dto.getScope() : SpendingLimit.Scope.CATEGORY;

        String categoryKey;
        String categoryName;
        if (scope == SpendingLimit.Scope.GENERAL) {
            categoryKey = GENERAL_CATEGORY_KEY;
            categoryName = GENERAL_CATEGORY_NAME;
        } else {
            Category category = getCategory(dto);
            categoryKey = normalizeKey(category.getName());
            categoryName = category.getName();
        }

        spendingLimitRepository
                .findByUserIdAndCategoryKeyAndMonthAndLimitType(userId, categoryKey, dto.getMonth(), limitType)
                .ifPresent(existing -> {
                    throw new RuntimeException(scope == SpendingLimit.Scope.GENERAL
                            ? "Ja existe um limite geral cadastrado para este mes"
                            : "Limite ja cadastrado para esta categoria e mes");
                });

        SpendingLimit limit = SpendingLimit.builder()
                .user(user)
                .categoryKey(categoryKey)
                .categoryName(categoryName)
                .month(dto.getMonth())
                .limitType(limitType)
                .scope(scope)
                .build();
        applyValues(limit, dto, categoryKey, categoryName, limitType, scope);

        return toDTO(spendingLimitRepository.save(limit));
    }

    public SpendingLimitDTO updateLimit(Long userId, Long id, SpendingLimitDTO dto) {
        SpendingLimit limit = getOwnedLimit(userId, id);
        SpendingLimit.LimitType limitType = dto.getLimitType() != null
                ? dto.getLimitType()
                : limit.getLimitType();
        SpendingLimit.Scope scope = dto.getScope() != null ? dto.getScope() : limit.getScope();

        String categoryKey;
        String categoryName;
        if (scope == SpendingLimit.Scope.GENERAL) {
            categoryKey = GENERAL_CATEGORY_KEY;
            categoryName = GENERAL_CATEGORY_NAME;
        } else {
            Category category = getCategory(dto);
            categoryKey = normalizeKey(category.getName());
            categoryName = category.getName();
        }

        spendingLimitRepository
                .findByUserIdAndCategoryKeyAndMonthAndLimitType(userId, categoryKey, dto.getMonth(), limitType)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException(scope == SpendingLimit.Scope.GENERAL
                            ? "Ja existe um limite geral cadastrado para este mes"
                            : "Limite ja cadastrado para esta categoria e mes");
                });

        applyValues(limit, dto, categoryKey, categoryName, limitType, scope);
        return toDTO(spendingLimitRepository.save(limit));
    }

    public void deleteLimit(Long userId, Long id) {
        spendingLimitRepository.delete(getOwnedLimit(userId, id));
    }

    private void applyValues(
            SpendingLimit limit,
            SpendingLimitDTO dto,
            String categoryKey,
            String categoryName,
            SpendingLimit.LimitType limitType,
            SpendingLimit.Scope scope) {
        BigDecimal amount = dto.getAmount();
        BigDecimal usedAmount = dto.getUsedAmount() != null ? dto.getUsedAmount() : BigDecimal.ZERO;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor limite deve ser maior que zero");
        }
        if (usedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Valor utilizado nao pode ser negativo");
        }
        if (usedAmount.compareTo(amount) > 0) {
            throw new RuntimeException("Valor utilizado nao pode ultrapassar o limite");
        }

        limit.setCategoryKey(categoryKey);
        limit.setCategoryName(categoryName);
        limit.setMonth(dto.getMonth());
        limit.setAmount(amount);
        limit.setUsedAmount(usedAmount);
        limit.setLimitType(limitType != null ? limitType : SpendingLimit.LimitType.EXPENSE);
        limit.setScope(scope != null ? scope : SpendingLimit.Scope.CATEGORY);
    }

    private SpendingLimit getOwnedLimit(Long userId, Long id) {
        SpendingLimit limit = spendingLimitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Limite nao encontrado"));
        if (!limit.getUser().getId().equals(userId)) {
            throw new RuntimeException("Limite nao pertence ao usuario");
        }
        return limit;
    }

    private Category getCategory(SpendingLimitDTO dto) {
        if (dto.getCategoryName() == null || dto.getCategoryName().isBlank()) {
            throw new RuntimeException("Categoria e obrigatoria");
        }
        return categoryService.getCategoryByName(dto.getCategoryName().trim());
    }

    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private SpendingLimitDTO toDTO(SpendingLimit limit) {
        BigDecimal amount = limit.getAmount() != null ? limit.getAmount() : BigDecimal.ZERO;
        BigDecimal usedAmount = limit.getUsedAmount() != null ? limit.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = amount.subtract(usedAmount).max(BigDecimal.ZERO);
        BigDecimal percentage = BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            percentage = usedAmount.multiply(BigDecimal.valueOf(100))
                    .divide(amount, 2, RoundingMode.HALF_UP);
        }

        return SpendingLimitDTO.builder()
                .id(limit.getId())
                .categoryKey(limit.getCategoryKey())
                .categoryName(limit.getCategoryName())
                .month(limit.getMonth())
                .amount(amount)
                .usedAmount(usedAmount)
                .limitType(limit.getLimitType() != null
                        ? limit.getLimitType()
                        : SpendingLimit.LimitType.EXPENSE)
                .scope(limit.getScope() != null
                        ? limit.getScope()
                        : SpendingLimit.Scope.CATEGORY)
                .spent(usedAmount)
                .remaining(remaining)
                .percentage(percentage)
                .build();
    }
}
