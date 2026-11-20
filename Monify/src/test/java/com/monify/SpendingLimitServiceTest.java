package com.monify;

import com.monify.dto.SpendingLimitDTO;
import com.monify.entity.Category;
import com.monify.entity.SpendingLimit;
import com.monify.entity.Transaction;
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
import java.time.LocalDate;

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
    private Category otherCategory;

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
        otherCategory = categoryRepository.save(Category.builder()
                .name("Transporte")
                .icon("T")
                .color("#4ECDC4")
                .build());
    }

    @Test
    void calculatesUsageFromCompletedExpensesOfTheCategoryAndMonth() {
        saveExpense("120.00", "2026-06-05", Transaction.TransactionStatus.COMPLETED, category);
        saveExpense("30.00", "2026-06-20", Transaction.TransactionStatus.COMPLETED, category);
        // nao entram: pendente, outro mes e outra categoria
        saveExpense("999.00", "2026-06-21", Transaction.TransactionStatus.PENDING, category);
        saveExpense("999.00", "2026-07-01", Transaction.TransactionStatus.COMPLETED, category);
        saveExpense("999.00", "2026-06-10", Transaction.TransactionStatus.COMPLETED, otherCategory);

        SpendingLimitDTO created = spendingLimitService.createLimit(user.getId(), limitDto("500.00"));

        assertNotNull(created.getId());
        assertEquals(new BigDecimal("150.00"), created.getUsedAmount());
        assertEquals(new BigDecimal("350.00"), created.getRemaining());
        assertEquals(new BigDecimal("30.00"), created.getPercentage());
    }

    @Test
    void generalLimitSumsEveryCategoryAndCanGoAbove100Percent() {
        saveExpense("400.00", "2026-06-05", Transaction.TransactionStatus.COMPLETED, category);
        saveExpense("200.00", "2026-06-06", Transaction.TransactionStatus.COMPLETED, otherCategory);

        SpendingLimitDTO dto = limitDto("300.00");
        dto.setScope(SpendingLimit.Scope.GENERAL);
        SpendingLimitDTO created = spendingLimitService.createLimit(user.getId(), dto);

        assertEquals(new BigDecimal("600.00"), created.getUsedAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(created.getRemaining()));
        assertEquals(new BigDecimal("200.00"), created.getPercentage());
    }

    @Test
    void ignoresManualUsageSentByClient() {
        SpendingLimitDTO dto = limitDto("500.00");
        dto.setUsedAmount(new BigDecimal("450.00"));

        SpendingLimitDTO created = spendingLimitService.createLimit(user.getId(), dto);

        assertEquals(new BigDecimal("0.00"), created.getUsedAmount());
    }

    @Test
    void rejectsDuplicatedLimit() {
        spendingLimitService.createLimit(user.getId(), limitDto("500.00"));

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                spendingLimitService.createLimit(user.getId(), limitDto("100.00")));

        assertEquals("Limite ja cadastrado para esta categoria e mes", error.getMessage());
    }

    private void saveExpense(String amount, String date, Transaction.TransactionStatus status, Category expenseCategory) {
        transactionRepository.save(Transaction.builder()
                .user(user)
                .category(expenseCategory)
                .amount(new BigDecimal(amount))
                .type(Transaction.TransactionType.EXPENSE)
                .description("Gasto de teste")
                .date(LocalDate.parse(date))
                .status(status)
                .build());
    }

    private SpendingLimitDTO limitDto(String amount) {
        return SpendingLimitDTO.builder()
                .categoryKey("alimentacao")
                .categoryName(category.getName())
                .month("2026-06")
                .amount(new BigDecimal(amount))
                .limitType(SpendingLimit.LimitType.EXPENSE)
                .build();
    }
}
