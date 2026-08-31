package com.monify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "spending_limits", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "category_key", "reference_month", "limit_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_key", nullable = false)
    private String categoryKey;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "reference_month", nullable = false, length = 7)
    private String month;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "used_amount", precision = 12, scale = 2)
    private BigDecimal usedAmount;

    @Column(name = "limit_type")
    @Enumerated(EnumType.STRING)
    private LimitType limitType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.limitType == null) {
            this.limitType = LimitType.EXPENSE;
        }
        if (this.usedAmount == null) {
            this.usedAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum LimitType {
        EXPENSE,
        INCOME
    }
}
