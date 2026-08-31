package com.monify;

import com.monify.dto.TransactionDTO;
import com.monify.entity.Category;
import com.monify.entity.Account;
import com.monify.entity.Transaction;
import com.monify.entity.User;
import com.monify.repository.CategoryRepository;
import com.monify.repository.AccountRepository;
import com.monify.repository.TransactionRepository;
import com.monify.repository.UserRepository;
import com.monify.service.CategoryService;
import com.monify.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    private User user;
    private Category category;
    private Account account;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("Usuario Categorias")
                .email("categorias@example.com")
                .password("password123")
                .build());
        category = categoryRepository.save(Category.builder()
                .name("Categoria Personalizada")
                .icon("C")
                .color("#123456")
                .build());
        account = accountRepository.save(Account.builder()
                .name("Conta principal")
                .type(Account.AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .user(user)
                .build());
    }

    @Test
    void listsLaunchCategoriesWithoutSerializingTransactions() throws Exception {
        transactionService.createTransaction(user.getId(), TransactionDTO.builder()
                .amount(new BigDecimal("25.00"))
                .type(Transaction.TransactionType.EXPENSE)
                .description("Teste de categoria")
                .date(LocalDate.of(2026, 6, 22))
                .categoryId(category.getId())
                .periodicity(Transaction.TransactionPeriodicity.SINGLE)
                .status(Transaction.TransactionStatus.COMPLETED)
                .originType(Transaction.MovementOrigin.ACCOUNT)
                .accountId(account.getId())
                .build());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(category.getId()))
                .andExpect(jsonPath("$[0].name").value(category.getName()))
                .andExpect(jsonPath("$[0].icon").value(category.getIcon()))
                .andExpect(jsonPath("$[0].color").value(category.getColor()))
                .andExpect(jsonPath("$[0].transactions").doesNotExist());
    }

    @Test
    void restoresDefaultsWithoutRemovingExistingCategories() {
        categoryService.initializeDefaultCategories();

        assertTrue(categoryRepository.findByName("Categoria Personalizada").isPresent());
        assertTrue(categoryRepository.findByName("Alimentação").isPresent());
        assertTrue(categoryRepository.findByName("Transporte").isPresent());
        assertTrue(categoryRepository.findByName("Saúde").isPresent());
        assertTrue(categoryRepository.findByName("Lazer").isPresent());
        assertTrue(categoryRepository.findByName("Salário").isPresent());
        assertTrue(categoryRepository.findByName("Educação").isPresent());
        assertTrue(categoryRepository.findByName("Utilidades").isPresent());
        assertTrue(categoryRepository.findByName("Outros").isPresent());
        assertTrue(categoryRepository.findByName("Moradia").isPresent());
        assertTrue(categoryRepository.findByName("Compras").isPresent());
        assertTrue(categoryRepository.findByName("Investimentos").isPresent());
        assertTrue(categoryRepository.findByName("Presentes").isPresent());
        assertEquals(13, categoryRepository.count());
    }
}
