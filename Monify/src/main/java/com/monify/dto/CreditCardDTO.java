package com.monify.dto;

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
public class CreditCardDTO {

    private Long id;

    @NotBlank(message = "Nome do cartao e obrigatorio")
    private String name;

    @NotNull(message = "Limite e obrigatorio")
    private BigDecimal limitAmount;

    private BigDecimal usedAmount;

    @NotBlank(message = "Final do cartao e obrigatorio")
    @Pattern(regexp = "\\d{4}", message = "Final deve conter 4 digitos")
    private String lastFour;

    @NotBlank(message = "Bandeira e obrigatoria")
    private String brand;
}
