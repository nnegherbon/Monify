package com.monify.service;

import com.monify.dto.AccountDTO;
import com.monify.entity.Account;
import com.monify.entity.User;
import com.monify.repository.AccountRepository;
import com.monify.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;

    public List<AccountDTO> getAccountsByUserId(Long userId) {
        userService.getUserById(userId);
        return accountRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public AccountDTO createAccount(Long userId, AccountDTO dto) {
        User user = userService.getUserById(userId);
        accountRepository.findByUserIdAndNameIgnoreCase(userId, dto.getName())
                .ifPresent(account -> {
                    throw new RuntimeException("Conta ja cadastrada");
                });

        Account account = Account.builder()
                .user(user)
                .name(dto.getName().trim())
                .type(dto.getType())
                .balance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO)
                .build();

        return toDTO(accountRepository.save(account));
    }

    public void deleteAccount(Long userId, Long id) {
        Account account = getOwnedAccount(userId, id);
        if (transactionRepository.existsByAccountId(id)) {
            throw new RuntimeException("A conta possui lancamentos vinculados e nao pode ser excluida");
        }
        accountRepository.delete(account);
    }

    public Account getOwnedAccount(Long userId, Long id) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Conta nao encontrada para o usuario"));
    }

    public void adjustBalance(Long userId, Long id, BigDecimal amount) {
        Account account = getOwnedAccount(userId, id);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    private AccountDTO toDTO(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .name(account.getName())
                .type(account.getType())
                .balance(account.getBalance())
                .build();
    }
}
