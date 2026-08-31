package com.monify.service;

import com.monify.dto.FinancialSummaryDTO;
import com.monify.dto.TransactionDTO;
import com.monify.entity.Account;
import com.monify.entity.Category;
import com.monify.entity.CreditCard;
import com.monify.entity.Transaction;
import com.monify.entity.User;
import com.monify.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final AccountService accountService;
    private final CreditCardService creditCardService;

    public Transaction createTransaction(Long userId, TransactionDTO dto) {
        User user = userService.getUserById(userId);
        Category category = categoryService.getCategoryById(dto.getCategoryId());

        validateInstallmentRequest(dto);
        if (getInstallmentCount(dto) > 1) {
            return createInstallmentTransactions(userId, user, category, dto);
        }

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .installmentNumber(1)
                .installmentCount(1)
                .installmentTotalAmount(dto.getAmount())
                .build();
        applyDTO(transaction, dto, userId, null);

        Transaction saved = transactionRepository.save(transaction);
        if (isCompleted(saved)) {
            applyFinancialImpact(saved, BigDecimal.ONE);
            createNextOccurrence(saved);
        }
        return saved;
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lancamento nao encontrado"));
    }

    public List<TransactionDTO> getTransactionsByUserId(Long userId) {
        userService.getUserById(userId);
        return toDTOList(transactionRepository.findByUserIdOrderByDateDesc(userId));
    }

    public List<TransactionDTO> getTransactionsByUserIdAndType(
            Long userId, Transaction.TransactionType type) {
        userService.getUserById(userId);
        return toDTOList(transactionRepository.findByUserIdAndType(userId, type));
    }

    public List<TransactionDTO> getPendingTransactionsByUserIdAndType(
            Long userId, Transaction.TransactionType type) {
        userService.getUserById(userId);
        return toDTOList(transactionRepository.findByUserIdAndTypeAndStatusOrderByDateAsc(
                userId, type, Transaction.TransactionStatus.PENDING));
    }

    public List<TransactionDTO> getTransactionsByUserIdAndCategory(Long userId, Long categoryId) {
        userService.getUserById(userId);
        categoryService.getCategoryById(categoryId);
        return toDTOList(transactionRepository.findByUserIdAndCategoryId(userId, categoryId));
    }

    public List<TransactionDTO> getTransactionsByUserIdTypeAndCategory(
            Long userId, Transaction.TransactionType type, Long categoryId) {
        userService.getUserById(userId);
        categoryService.getCategoryById(categoryId);
        return toDTOList(transactionRepository.findByUserIdAndTypeAndCategoryId(userId, type, categoryId));
    }

    public Transaction updateTransaction(Long id, TransactionDTO dto) {
        Transaction transaction = getTransactionById(id);
        Long userId = transaction.getUser().getId();
        boolean wasCompleted = isCompleted(transaction);

        if (wasCompleted) {
            applyFinancialImpact(transaction, BigDecimal.ONE.negate());
        }

        Category category = categoryService.getCategoryById(dto.getCategoryId());
        transaction.setCategory(category);
        applyDTO(transaction, dto, userId, transaction.getStatus());

        Transaction saved = transactionRepository.save(transaction);
        if (isCompleted(saved)) {
            applyFinancialImpact(saved, BigDecimal.ONE);
            if (!wasCompleted) {
                createNextOccurrence(saved);
            }
        }
        return saved;
    }

    public Transaction settleTransaction(Long userId, Long id) {
        Transaction transaction = getOwnedTransaction(userId, id);
        if (isCompleted(transaction)) {
            throw new RuntimeException("Lancamento ja foi efetivado");
        }
        validatePersistedOrigin(transaction);

        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setSettledAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);
        applyFinancialImpact(saved, BigDecimal.ONE);
        createNextOccurrence(saved);
        return saved;
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = getTransactionById(id);
        if (isCompleted(transaction)) {
            applyFinancialImpact(transaction, BigDecimal.ONE.negate());
        }
        transactionRepository.delete(transaction);
    }

    public FinancialSummaryDTO getFinancialSummary(Long userId) {
        userService.getUserById(userId);

        BigDecimal totalIncome = defaultZero(transactionRepository.getTotalIncome(userId));
        BigDecimal totalExpense = defaultZero(transactionRepository.getTotalExpense(userId));
        Long transactionCount = transactionRepository.getTransactionCount(userId);
        Map<String, BigDecimal> expensesByCategory = new HashMap<>();

        List<Object[]> results = transactionRepository.getExpensesByCategory(userId);
        if (results != null) {
            for (Object[] result : results) {
                if (result != null && result.length >= 2) {
                    expensesByCategory.put((String) result[0], (BigDecimal) result[1]);
                }
            }
        }

        return FinancialSummaryDTO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .transactionCount(transactionCount != null ? transactionCount : 0L)
                .userId(userId)
                .expensesByCategory(expensesByCategory)
                .build();
    }

    public List<TransactionDTO> getTransactionsByDateRange(
            Long userId, LocalDate startDate, LocalDate endDate) {
        userService.getUserById(userId);
        return toDTOList(transactionRepository.findByUserIdAndDateRange(userId, startDate, endDate));
    }

    public void initializeLegacyFields() {
        transactionRepository.initializeLegacyPendingStatuses();
        transactionRepository.initializeLegacyCompletedStatuses();
        transactionRepository.initializeLegacyPeriodicities();
        transactionRepository.initializeLegacyInstallmentNumbers();
        transactionRepository.initializeLegacyInstallmentCounts();
        transactionRepository.initializeLegacyInstallmentTotals();
    }

    public TransactionDTO convertToDTO(Transaction transaction) {
        Account account = transaction.getAccount();
        CreditCard card = transaction.getCreditCard();
        Transaction.MovementOrigin originType = transaction.getOriginType();
        if (originType == null) {
            originType = account != null
                    ? Transaction.MovementOrigin.ACCOUNT
                    : card != null ? Transaction.MovementOrigin.CREDIT_CARD : null;
        }

        return TransactionDTO.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .date(transaction.getDate())
                .periodicity(defaultPeriodicity(transaction.getPeriodicity()))
                .status(defaultStatus(transaction))
                .settledAt(transaction.getSettledAt())
                .originType(originType)
                .accountId(account != null ? account.getId() : null)
                .creditCardId(card != null ? card.getId() : null)
                .installmentGroupKey(transaction.getInstallmentGroupKey())
                .installmentNumber(defaultInstallmentNumber(transaction))
                .installmentCount(defaultInstallmentCount(transaction))
                .installmentTotalAmount(transaction.getInstallmentTotalAmount() != null
                        ? transaction.getInstallmentTotalAmount()
                        : transaction.getAmount())
                .originName(account != null ? account.getName() : card != null ? card.getName() : "Nao informado")
                .originDescription(originDescription(account, card))
                .categoryId(transaction.getCategory().getId())
                .categoryName(transaction.getCategory().getName())
                .categoryIcon(transaction.getCategory().getIcon())
                .categoryColor(transaction.getCategory().getColor())
                .userId(transaction.getUser().getId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private void applyDTO(
            Transaction transaction,
            TransactionDTO dto,
            Long userId,
            Transaction.TransactionStatus currentStatus) {
        transaction.setAmount(dto.getAmount());
        transaction.setType(dto.getType());
        transaction.setDescription(dto.getDescription().trim());
        transaction.setDate(dto.getDate());

        Transaction.TransactionPeriodicity periodicity = defaultPeriodicity(dto.getPeriodicity());
        transaction.setPeriodicity(periodicity);
        if (periodicity == Transaction.TransactionPeriodicity.SINGLE) {
            transaction.setRecurrenceKey(null);
        } else if (transaction.getRecurrenceKey() == null) {
            transaction.setRecurrenceKey(UUID.randomUUID().toString());
        }

        Transaction.TransactionStatus status = dto.getStatus();
        if (status == null) {
            status = currentStatus != null
                    ? currentStatus
                    : dto.getDate().isAfter(LocalDate.now())
                        ? Transaction.TransactionStatus.PENDING
                        : Transaction.TransactionStatus.COMPLETED;
        }
        transaction.setStatus(status);
        transaction.setSettledAt(status == Transaction.TransactionStatus.COMPLETED
                ? transaction.getSettledAt() != null ? transaction.getSettledAt() : LocalDateTime.now()
                : null);

        resolveOrigin(transaction, dto, userId);
    }

    private void resolveOrigin(Transaction transaction, TransactionDTO dto, Long userId) {
        Transaction.MovementOrigin originType = dto.getOriginType();
        if (originType == null) {
            if (dto.getAccountId() != null && dto.getCreditCardId() == null) {
                originType = Transaction.MovementOrigin.ACCOUNT;
            } else if (dto.getCreditCardId() != null && dto.getAccountId() == null) {
                originType = Transaction.MovementOrigin.CREDIT_CARD;
            }
        }

        if (originType == Transaction.MovementOrigin.ACCOUNT) {
            if (dto.getAccountId() == null || dto.getCreditCardId() != null) {
                throw new RuntimeException("Selecione uma unica conta de origem");
            }
            Account account = accountService.getOwnedAccount(userId, dto.getAccountId());
            if (account.getType() != Account.AccountType.CHECKING
                    && account.getType() != Account.AccountType.SAVINGS) {
                throw new RuntimeException("A origem deve ser uma conta corrente ou poupanca");
            }
            transaction.setOriginType(Transaction.MovementOrigin.ACCOUNT);
            transaction.setAccount(account);
            transaction.setCreditCard(null);
            return;
        }

        if (originType == Transaction.MovementOrigin.CREDIT_CARD) {
            if (dto.getCreditCardId() == null || dto.getAccountId() != null) {
                throw new RuntimeException("Selecione um unico cartao de origem");
            }
            if (dto.getType() != Transaction.TransactionType.EXPENSE) {
                throw new RuntimeException("Cartao de credito so pode ser usado em despesas");
            }
            transaction.setOriginType(Transaction.MovementOrigin.CREDIT_CARD);
            transaction.setCreditCard(creditCardService.getOwnedCard(userId, dto.getCreditCardId()));
            transaction.setAccount(null);
            return;
        }

        throw new RuntimeException("Origem da movimentacao e obrigatoria");
    }

    private Transaction createInstallmentTransactions(
            Long userId,
            User user,
            Category category,
            TransactionDTO dto) {
        int count = getInstallmentCount(dto);
        BigDecimal total = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal regularAmount = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO;
        String groupKey = UUID.randomUUID().toString();
        List<Transaction> savedInstallments = new ArrayList<>();

        for (int index = 1; index <= count; index++) {
            BigDecimal installmentAmount = index == count
                    ? total.subtract(allocated)
                    : regularAmount;
            allocated = allocated.add(installmentAmount);

            TransactionDTO installmentDTO = copyForInstallment(
                    dto,
                    installmentAmount,
                    dto.getDate().plusMonths(index - 1L),
                    index,
                    count);
            Transaction installment = Transaction.builder()
                    .user(user)
                    .category(category)
                    .installmentGroupKey(groupKey)
                    .installmentNumber(index)
                    .installmentCount(count)
                    .installmentTotalAmount(total)
                    .build();
            applyDTO(installment, installmentDTO, userId, null);

            Transaction saved = transactionRepository.save(installment);
            if (isCompleted(saved)) {
                applyFinancialImpact(saved, BigDecimal.ONE);
            }
            savedInstallments.add(saved);
        }

        return savedInstallments.get(0);
    }

    private TransactionDTO copyForInstallment(
            TransactionDTO source,
            BigDecimal amount,
            LocalDate date,
            int number,
            int count) {
        return TransactionDTO.builder()
                .amount(amount)
                .type(source.getType())
                .description(source.getDescription().trim() + " - Parcela " + number + "/" + count)
                .date(date)
                .periodicity(Transaction.TransactionPeriodicity.SINGLE)
                .status(source.getStatus())
                .originType(source.getOriginType())
                .accountId(source.getAccountId())
                .creditCardId(source.getCreditCardId())
                .categoryId(source.getCategoryId())
                .installmentNumber(number)
                .installmentCount(count)
                .installmentTotalAmount(source.getAmount())
                .build();
    }

    private void validateInstallmentRequest(TransactionDTO dto) {
        int count = getInstallmentCount(dto);
        if (count < 1) {
            throw new RuntimeException("Quantidade de parcelas deve ser pelo menos 1");
        }
        if (count == 1) {
            return;
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor total deve ser maior que zero");
        }
        if (dto.getType() != Transaction.TransactionType.EXPENSE) {
            throw new RuntimeException("Parcelamento e permitido apenas para despesas");
        }
        if (dto.getOriginType() != Transaction.MovementOrigin.CREDIT_CARD
                || dto.getCreditCardId() == null
                || dto.getAccountId() != null) {
            throw new RuntimeException("Parcelamento e permitido apenas no cartao de credito");
        }
        if (defaultPeriodicity(dto.getPeriodicity()) != Transaction.TransactionPeriodicity.SINGLE) {
            throw new RuntimeException("Compra parcelada nao pode ter recorrencia mensal ou anual");
        }
    }

    private int getInstallmentCount(TransactionDTO dto) {
        return dto.getInstallmentCount() != null ? dto.getInstallmentCount() : 1;
    }

    private void applyFinancialImpact(Transaction transaction, BigDecimal multiplier) {
        BigDecimal amount = transaction.getAmount().multiply(multiplier);
        Long userId = transaction.getUser().getId();

        if (transaction.getAccount() != null) {
            if (transaction.getType() == Transaction.TransactionType.EXPENSE) {
                amount = amount.negate();
            }
            accountService.adjustBalance(userId, transaction.getAccount().getId(), amount);
            return;
        }

        if (transaction.getCreditCard() != null) {
            if (transaction.getType() != Transaction.TransactionType.EXPENSE) {
                throw new RuntimeException("Cartao de credito so pode ser usado em despesas");
            }
            creditCardService.adjustUsedAmount(
                    userId, transaction.getCreditCard().getId(), transaction.getAmount().multiply(multiplier));
            return;
        }

        throw new RuntimeException("Lancamento sem origem financeira valida");
    }

    private void createNextOccurrence(Transaction transaction) {
        Transaction.TransactionPeriodicity periodicity = defaultPeriodicity(transaction.getPeriodicity());
        if (periodicity == Transaction.TransactionPeriodicity.SINGLE) {
            return;
        }

        LocalDate nextDate = periodicity == Transaction.TransactionPeriodicity.MONTHLY
                ? transaction.getDate().plusMonths(1)
                : transaction.getDate().plusYears(1);
        String recurrenceKey = transaction.getRecurrenceKey();
        if (recurrenceKey == null) {
            recurrenceKey = UUID.randomUUID().toString();
            transaction.setRecurrenceKey(recurrenceKey);
            transactionRepository.save(transaction);
        }
        if (transactionRepository.existsByRecurrenceKeyAndDate(recurrenceKey, nextDate)) {
            return;
        }

        Transaction next = Transaction.builder()
                .user(transaction.getUser())
                .category(transaction.getCategory())
                .account(transaction.getAccount())
                .creditCard(transaction.getCreditCard())
                .originType(transaction.getOriginType())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .date(nextDate)
                .periodicity(periodicity)
                .status(Transaction.TransactionStatus.PENDING)
                .recurrenceKey(recurrenceKey)
                .installmentNumber(1)
                .installmentCount(1)
                .installmentTotalAmount(transaction.getAmount())
                .build();
        transactionRepository.save(next);
    }

    private Transaction getOwnedTransaction(Long userId, Long id) {
        Transaction transaction = getTransactionById(id);
        if (!transaction.getUser().getId().equals(userId)) {
            throw new RuntimeException("Lancamento nao pertence ao usuario");
        }
        return transaction;
    }

    private void validatePersistedOrigin(Transaction transaction) {
        if (transaction.getAccount() == null && transaction.getCreditCard() == null) {
            throw new RuntimeException("Edite o lancamento e selecione a origem antes de efetivar");
        }
    }

    private boolean isCompleted(Transaction transaction) {
        return defaultStatus(transaction) == Transaction.TransactionStatus.COMPLETED;
    }

    private Transaction.TransactionStatus defaultStatus(Transaction transaction) {
        if (transaction.getStatus() != null) {
            return transaction.getStatus();
        }
        return transaction.getDate().isBefore(LocalDate.now())
                ? Transaction.TransactionStatus.COMPLETED
                : Transaction.TransactionStatus.PENDING;
    }

    private Transaction.TransactionPeriodicity defaultPeriodicity(
            Transaction.TransactionPeriodicity periodicity) {
        return periodicity != null ? periodicity : Transaction.TransactionPeriodicity.SINGLE;
    }

    private String originDescription(Account account, CreditCard card) {
        if (account != null) {
            return account.getType().getDisplayName() + " - " + account.getName();
        }
        if (card != null) {
            return "Cartao de credito - " + card.getName() + " final " + card.getLastFour();
        }
        return "Origem nao informada";
    }

    private List<TransactionDTO> toDTOList(List<Transaction> transactions) {
        return transactions.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int defaultInstallmentNumber(Transaction transaction) {
        return transaction.getInstallmentNumber() != null ? transaction.getInstallmentNumber() : 1;
    }

    private int defaultInstallmentCount(Transaction transaction) {
        return transaction.getInstallmentCount() != null ? transaction.getInstallmentCount() : 1;
    }
}
