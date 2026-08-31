package com.monify.dto;

import com.monify.entity.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDTO {

    private Long id;

    @NotBlank(message = "Nome da conta e obrigatorio")
    private String name;

    @NotNull(message = "Tipo da conta e obrigatorio")
    private Account.AccountType type;

    @NotNull(message = "Saldo e obrigatorio")
    private BigDecimal balance;
}
