package com.monify.service;

import com.monify.dto.CreditCardDTO;
import com.monify.entity.CreditCard;
import com.monify.entity.User;
import com.monify.repository.CreditCardRepository;
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
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;

    public List<CreditCardDTO> getCardsByUserId(Long userId) {
        userService.getUserById(userId);
        return creditCardRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CreditCardDTO createCard(Long userId, CreditCardDTO dto) {
        User user = userService.getUserById(userId);
        creditCardRepository.findByUserIdAndNameIgnoreCase(userId, dto.getName())
                .ifPresent(card -> {
                    throw new RuntimeException("Cartao ja cadastrado");
                });

        CreditCard card = CreditCard.builder()
                .user(user)
                .name(dto.getName().trim())
                .limitAmount(dto.getLimitAmount() != null ? dto.getLimitAmount() : BigDecimal.ZERO)
                .usedAmount(BigDecimal.ZERO)
                .lastFour(dto.getLastFour())
                .brand(dto.getBrand().trim())
                .build();

        return toDTO(creditCardRepository.save(card));
    }

    public void deleteCard(Long userId, Long id) {
        CreditCard card = getOwnedCard(userId, id);
        if (transactionRepository.existsByCreditCardId(id)) {
            throw new RuntimeException("O cartao possui lancamentos vinculados e nao pode ser excluido");
        }
        creditCardRepository.delete(card);
    }

    public CreditCard getOwnedCard(Long userId, Long id) {
        return creditCardRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Cartao nao encontrado para o usuario"));
    }

    public void adjustUsedAmount(Long userId, Long id, BigDecimal amount) {
        CreditCard card = getOwnedCard(userId, id);
        BigDecimal current = card.getUsedAmount() != null ? card.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal next = current.add(amount).max(BigDecimal.ZERO);
        if (amount.signum() > 0 && next.compareTo(card.getLimitAmount()) > 0) {
            throw new RuntimeException("A despesa ultrapassa o limite disponivel do cartao");
        }
        card.setUsedAmount(next);
        creditCardRepository.save(card);
    }

    private CreditCardDTO toDTO(CreditCard card) {
        return CreditCardDTO.builder()
                .id(card.getId())
                .name(card.getName())
                .limitAmount(card.getLimitAmount())
                .usedAmount(card.getUsedAmount() != null ? card.getUsedAmount() : BigDecimal.ZERO)
                .lastFour(card.getLastFour())
                .brand(card.getBrand())
                .build();
    }
}
