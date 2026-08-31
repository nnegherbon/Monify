package com.monify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private TransactionPeriodicity periodicity;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type")
    private MovementOrigin originType;

    @Column(name = "recurrence_key", length = 36)
    private String recurrenceKey;

    @Column(name = "installment_group_key", length = 36)
    private String installmentGroupKey;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installment_count")
    private Integer installmentCount;

    @Column(name = "installment_total_amount", precision = 12, scale = 2)
    private BigDecimal installmentTotalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id")
    private CreditCard creditCard;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.periodicity == null) {
            this.periodicity = TransactionPeriodicity.SINGLE;
        }
        if (this.status == null) {
            this.status = TransactionStatus.PENDING;
        }
        if (this.installmentNumber == null) {
            this.installmentNumber = 1;
        }
        if (this.installmentCount == null) {
            this.installmentCount = 1;
        }
        if (this.installmentTotalAmount == null) {
            this.installmentTotalAmount = this.amount;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum TransactionType {
        INCOME("Receita"),
        EXPENSE("Despesa");

        private final String displayName;

        TransactionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum TransactionPeriodicity {
        SINGLE("Unico"),
        MONTHLY("Mensal"),
        ANNUAL("Anual");

        private final String displayName;

        TransactionPeriodicity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum TransactionStatus {
        PENDING,
        COMPLETED
    }

    public enum MovementOrigin {
        ACCOUNT,
        CREDIT_CARD
    }
}
