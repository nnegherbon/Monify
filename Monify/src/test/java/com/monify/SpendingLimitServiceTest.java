package com.monify;

import com.monify.dto.SpendingLimitDTO;
import com.monify.entity.Category;
import com.monify.entity.SpendingLimit;
import com.monify.entity.User;
import com.monify.repository.CategoryRepository;
import com.monify.repository.SpendingLimitRepository;
import com.monify.repository.TransactionRepository;
import com.monify.repository.UserRepository;
import com.monify.service.SpendingLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class SpendingLimitServiceTest {

    @Autowired
    private SpendingLimitService spendingLimitService;

    @Autowired
    private SpendingLimitRepository spendingLimitRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        spendingLimitRepository.deleteAll();
        transactionRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("Usuario Teste")
                .email("limites@example.com")
                .password("password123")
                .build());
        category = categoryRepository.save(Category.builder()
                .name("Alimentacao")
                .icon("A")
                .color("#FF6B6B")
                .build());
    }

    @Test
    void createsLimitWithPersistedUsageAndCalculatedValues() {
        SpendingLimitDTO created = spendingLimitService.createLimit(
                user.getId(), limitDto("500.00", "100.00"));

        assertNotNull(created.getId());
        assertEquals(new BigDecimal("100.00"), created.getUsedAmount());
        assertEquals(new BigDecimal("400.00"), created.getRemaining());
        assertEquals(new BigDecimal("20.00"), created.getPercentage());
    }

    @Test
    void updatesPersistedUsage() {
        SpendingLimitDTO created = spendingLimitService.createLimit(
                user.getId(), limitDto("500.00", "100.00"));

        SpendingLimitDTO updated = spendingLimitService.updateLimit(
                user.getId(), created.getId(), limitDto("500.00", "500.00"));

        assertEquals(new BigDecimal("500.00"), updated.getUsedAmount());
        assertEquals(new BigDecimal("0.00"), updated.getRemaining());
        assertEquals(new BigDecimal("100.00"), updated.getPercentage());
    }

    @Test
    void rejectsUsageAboveLimit() {
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                spendingLimitService.createLimit(user.getId(), limitDto("500.00", "500.01")));

        assertEquals("Valor utilizado nao pode ultrapassar o limite", error.getMessage());
    }

    private SpendingLimitDTO limitDto(String amount, String usedAmount) {
        return SpendingLimitDTO.builder()
                .categoryKey("alimentacao")
                .categoryName(category.getName())
                .month("2026-06")
                .amount(new BigDecimal(amount))
                .usedAmount(new BigDecimal(usedAmount))
                .limitType(SpendingLimit.LimitType.EXPENSE)
                .build();
    }
}
