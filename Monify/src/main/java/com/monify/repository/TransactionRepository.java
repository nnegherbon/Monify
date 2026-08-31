package com.monify.repository;

import com.monify.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByUserIdAndType(Long userId, Transaction.TransactionType type);

    List<Transaction> findByUserIdAndCategoryId(Long userId, Long categoryId);

    List<Transaction> findByUserIdAndTypeAndCategoryId(Long userId, Transaction.TransactionType type, Long categoryId);

    List<Transaction> findByUserIdOrderByDateDesc(Long userId);

    List<Transaction> findByUserIdAndTypeAndStatusOrderByDateAsc(
            Long userId,
            Transaction.TransactionType type,
            Transaction.TransactionStatus status);

    boolean existsByRecurrenceKeyAndDate(String recurrenceKey, LocalDate date);

    boolean existsByAccountId(Long accountId);

    boolean existsByCreditCardId(Long creditCardId);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId AND t.type = 'INCOME' AND t.status = 'COMPLETED'")
    BigDecimal getTotalIncome(@Param("userId") Long userId);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.status = 'COMPLETED'")
    BigDecimal getTotalExpense(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId AND t.status = 'COMPLETED'")
    Long getTransactionCount(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.date BETWEEN :startDate AND :endDate ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT t.category.name, SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.status = 'COMPLETED' GROUP BY t.category.name")
    List<Object[]> getExpensesByCategory(@Param("userId") Long userId);

    @Query("SELECT t.type, SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId AND t.status = 'COMPLETED' GROUP BY t.type")
    List<Object[]> getTotalsByType(@Param("userId") Long userId);

    @Modifying
    @Query(value = "UPDATE transactions SET status = 'PENDING' WHERE status IS NULL AND date >= CURRENT_DATE", nativeQuery = true)
    int initializeLegacyPendingStatuses();

    @Modifying
    @Query(value = "UPDATE transactions SET status = 'COMPLETED' WHERE status IS NULL", nativeQuery = true)
    int initializeLegacyCompletedStatuses();

    @Modifying
    @Query(value = "UPDATE transactions SET periodicity = 'SINGLE' WHERE periodicity IS NULL", nativeQuery = true)
    int initializeLegacyPeriodicities();

    @Modifying
    @Query(value = "UPDATE transactions SET installment_number = 1 WHERE installment_number IS NULL", nativeQuery = true)
    int initializeLegacyInstallmentNumbers();

    @Modifying
    @Query(value = "UPDATE transactions SET installment_count = 1 WHERE installment_count IS NULL", nativeQuery = true)
    int initializeLegacyInstallmentCounts();

    @Modifying
    @Query(value = "UPDATE transactions SET installment_total_amount = amount WHERE installment_total_amount IS NULL", nativeQuery = true)
    int initializeLegacyInstallmentTotals();
}
