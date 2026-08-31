package com.monify.config;

import com.monify.service.CategoryService;
import com.monify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryService categoryService;
    private final TransactionService transactionService;

    @Override
    public void run(String... args) throws Exception {
        categoryService.initializeDefaultCategories();
        transactionService.initializeLegacyFields();
    }
}
