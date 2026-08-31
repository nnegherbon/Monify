package com.monify.dto;

import com.monify.entity.SpendingLimit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingLimitDTO {

    private Long id;

    @NotBlank(message = "Categoria e obrigatoria")
    private String categoryKey;

    @NotBlank(message = "Nome da categoria e obrigatorio")
    private String categoryName;

    @NotBlank(message = "Mes de referencia e obrigatorio")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Mes deve estar no formato yyyy-MM")
    private String month;

    @NotNull(message = "Valor limite e obrigatorio")
    @DecimalMin(value = "0.01", message = "Valor limite deve ser maior que zero")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Valor utilizado nao pode ser negativo")
    private BigDecimal usedAmount;

    private SpendingLimit.LimitType limitType;

    private BigDecimal spent;
    private BigDecimal remaining;
    private BigDecimal percentage;
}
