package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.*;
import org.example.dto.request.AccountRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.exception.GlobalExceptionHandler.*;
import org.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final BankAccountRepository bankAccountRepo;
    private final TradingAccountRepository tradingAccountRepo;
    private final CashOperationRepository cashOpRepo;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // -------------------------------------------------------
    //  Bank accounts
    // -------------------------------------------------------

    @Transactional
    public BankAccountResponse openBankAccount(String username, CreateBankAccountRequest request) {
        UserEntity user = findUser(username);
        BankAccountEntity account = BankAccountEntity.builder()
                .user(user)
                .accountNumber(generateAccountNumber("B"))
                .currency(request.currency())
                .createdAt(LocalDateTime.now())
                .build();
        account = bankAccountRepo.save(account);
        auditService.logSuccess(username, "OPEN_BANK_ACCOUNT", "BANK_ACCOUNT", account.getId().toString());
        return toBankResponse(account);
    }

    public List<BankAccountResponse> getBankAccounts(String username) {
        UserEntity user = findUser(username);
        return bankAccountRepo.findByUserId(user.getId()).stream().map(this::toBankResponse).toList();
    }

    public BankAccountResponse getBankAccount(Long id, String username) {
        BankAccountEntity account = bankAccountRepo.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Bank account not found: " + id));
        ensureOwner(account.getUser().getUsername(), username);
        return toBankResponse(account);
    }

    @Transactional
    public BankAccountResponse closeBankAccount(Long id, String username) {
        BankAccountEntity account = bankAccountRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException("Bank account not found: " + id));
        ensureOwner(account.getUser().getUsername(), username);
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0)
            throw new TradingException("Cannot close account with positive balance");
        account.setStatus("CLOSED");
        bankAccountRepo.save(account);
        auditService.logSuccess(username, "CLOSE_BANK_ACCOUNT", "BANK_ACCOUNT", id.toString());
        return toBankResponse(account);
    }

    @Transactional
    public BankAccountResponse deposit(String username, DepositRequest request) {
        BankAccountEntity account = bankAccountRepo.findByIdForUpdate(request.bankAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account.getUser().getUsername(), username);
        checkAccountActive(account);

        account.setBalance(account.getBalance().add(request.amount()));
        bankAccountRepo.save(account);

        recordCashOp(account, "DEPOSIT", request.amount(), request.description());
        auditService.logSuccess(username, "DEPOSIT", "BANK_ACCOUNT", account.getId().toString());
        notificationService.notify(account.getUser(), "DEPOSIT",
                "Deposit successful",
                "Deposited " + request.amount() + " " + account.getCurrency());
        return toBankResponse(account);
    }

    @Transactional
    public BankAccountResponse withdraw(String username, WithdrawRequest request) {
        BankAccountEntity account = bankAccountRepo.findByIdForUpdate(request.bankAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account.getUser().getUsername(), username);
        checkAccountActive(account);

        if (account.getBalance().compareTo(request.amount()) < 0)
            throw new InsufficientFundsException("Insufficient balance for withdrawal");

        account.setBalance(account.getBalance().subtract(request.amount()));
        bankAccountRepo.save(account);

        recordCashOp(account, "WITHDRAWAL", request.amount(), request.description());
        auditService.logSuccess(username, "WITHDRAWAL", "BANK_ACCOUNT", account.getId().toString());
        notificationService.notify(account.getUser(), "WITHDRAWAL",
                "Withdrawal successful",
                "Withdrawn " + request.amount() + " " + account.getCurrency());
        return toBankResponse(account);
    }

    @Transactional
    public void transfer(String username, TransferRequest request) {
        BankAccountEntity from = bankAccountRepo.findByIdForUpdate(request.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
        BankAccountEntity to = bankAccountRepo.findByIdForUpdate(request.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

        ensureOwner(from.getUser().getUsername(), username);
        checkAccountActive(from);
        checkAccountActive(to);

        if (from.getBalance().compareTo(request.amount()) < 0)
            throw new InsufficientFundsException("Insufficient balance for transfer");

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));
        bankAccountRepo.save(from);
        bankAccountRepo.save(to);

        String ref = UUID.randomUUID().toString();
        recordCashOpWithRef(from, "TRANSFER_OUT", request.amount(), request.description(), ref);
        recordCashOpWithRef(to,   "TRANSFER_IN",  request.amount(), request.description(), ref);
        auditService.logSuccess(username, "TRANSFER", "BANK_ACCOUNT",
                from.getId() + "->" + to.getId());
    }

    public List<CashOperationResponse> getAccountStatement(Long accountId, String username) {
        BankAccountEntity account = bankAccountRepo.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        ensureOwner(account.getUser().getUsername(), username);
        return cashOpRepo.findByBankAccountIdOrderByCreatedAtDesc(accountId)
                .stream().map(this::toCashOpResponse).toList();
    }

    // -------------------------------------------------------
    //  Trading accounts
    // -------------------------------------------------------

    @Transactional
    public TradingAccountResponse openTradingAccount(String username, CreateTradingAccountRequest request) {
        UserEntity user = findUser(username);
        TradingAccountEntity account = TradingAccountEntity.builder()
                .user(user)
                .accountNumber(generateAccountNumber("T"))
                .currency(request.currency())
                .createdAt(LocalDateTime.now())
                .build();
        account = tradingAccountRepo.save(account);
        auditService.logSuccess(username, "OPEN_TRADING_ACCOUNT", "TRADING_ACCOUNT", account.getId().toString());
        return toTradingResponse(account);
    }

    public List<TradingAccountResponse> getTradingAccounts(String username) {
        UserEntity user = findUser(username);
        return tradingAccountRepo.findByUserId(user.getId()).stream().map(this::toTradingResponse).toList();
    }

    public TradingAccountResponse getTradingAccount(Long id, String username) {
        TradingAccountEntity account = tradingAccountRepo.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Trading account not found: " + id));
        ensureOwner(account.getUser().getUsername(), username);
        return toTradingResponse(account);
    }

    @Transactional
    public void fundTradingAccount(String username, FundTradingAccountRequest request) {
        BankAccountEntity bank = bankAccountRepo.findByIdForUpdate(request.bankAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Bank account not found"));
        TradingAccountEntity trading = tradingAccountRepo.findByIdForUpdate(request.tradingAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Trading account not found"));

        ensureOwner(bank.getUser().getUsername(), username);
        ensureOwner(trading.getUser().getUsername(), username);
        checkAccountActive(bank);

        if (bank.getBalance().compareTo(request.amount()) < 0)
            throw new InsufficientFundsException("Insufficient bank balance");

        bank.setBalance(bank.getBalance().subtract(request.amount()));
        trading.setCashBalance(trading.getCashBalance().add(request.amount()));
        bankAccountRepo.save(bank);
        tradingAccountRepo.save(trading);

        recordCashOp(bank, "TRANSFER_OUT", request.amount(), "Fund trading account");
        auditService.logSuccess(username, "FUND_TRADING_ACCOUNT", "TRADING_ACCOUNT",
                trading.getId().toString());
    }

    // -------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------

    private void recordCashOp(BankAccountEntity account, String type, BigDecimal amount, String desc) {
        recordCashOpWithRef(account, type, amount, desc, null);
    }

    private void recordCashOpWithRef(BankAccountEntity account, String type,
                                     BigDecimal amount, String desc, String ref) {
        CashOperationEntity op = CashOperationEntity.builder()
                .bankAccount(account)
                .user(account.getUser())
                .operationType(type)
                .amount(amount)
                .currency(account.getCurrency())
                .description(desc)
                .referenceId(ref)
                .createdAt(LocalDateTime.now())
                .build();
        cashOpRepo.save(op);
    }

    private void checkAccountActive(BankAccountEntity account) {
        if (!"ACTIVE".equals(account.getStatus()))
            throw new AccountFrozenException("Account " + account.getAccountNumber() + " is not active");
    }

    private void ensureOwner(String accountOwner, String requestingUser) {
        if (!accountOwner.equals(requestingUser))
            throw new AccountNotFoundException("Account not found or access denied");
    }

    private UserEntity findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    private String generateAccountNumber(String prefix) {
        return prefix + System.currentTimeMillis() % 10_000_000_000L;
    }

    BankAccountResponse toBankResponse(BankAccountEntity a) {
        return new BankAccountResponse(a.getId(), a.getAccountNumber(),
                a.getCurrency(), a.getBalance(), a.getStatus(), a.getCreatedAt());
    }

    TradingAccountResponse toTradingResponse(TradingAccountEntity a) {
        return new TradingAccountResponse(a.getId(), a.getAccountNumber(), a.getCurrency(),
                a.getCashBalance(), a.getFrozenBalance(), a.getAvailableBalance(),
                a.getStatus(), a.getCreatedAt());
    }

    private CashOperationResponse toCashOpResponse(CashOperationEntity op) {
        return new CashOperationResponse(op.getId(),
                op.getBankAccount().getAccountNumber(), op.getOperationType(),
                op.getAmount(), op.getCurrency(), op.getStatus(),
                op.getDescription(), op.getCreatedAt());
    }
}
