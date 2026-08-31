package com.monify.dto;

import com.monify.entity.Transaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDTO {

    private Long id;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "Tipo é obrigatório")
    private Transaction.TransactionType type;

    @NotBlank(message = "Descrição é obrigatória")
    private String description;

    @NotNull(message = "Data é obrigatória")
    private LocalDate date;

    private Transaction.TransactionPeriodicity periodicity;

    private Transaction.TransactionStatus status;

    private Transaction.MovementOrigin originType;

    private Long accountId;

    private Long creditCardId;

    private String installmentGroupKey;

    private Integer installmentNumber;

    private Integer installmentCount;

    private BigDecimal installmentTotalAmount;

    @NotNull(message = "Categoria é obrigatória")
    private Long categoryId;

    private Long userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Informações adicionais para exibição
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private String originName;
    private String originDescription;
    private LocalDateTime settledAt;
}
