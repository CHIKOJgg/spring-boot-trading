package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.request.AccountRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // -------------------------------------------------------
    //  Bank accounts
    // -------------------------------------------------------

    @PostMapping("/bank")
    public ResponseEntity<BankAccountResponse> openBankAccount(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CreateBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.openBankAccount(user.getUsername(), request));
    }

    @GetMapping("/bank")
    public ResponseEntity<List<BankAccountResponse>> getBankAccounts(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.getBankAccounts(user.getUsername()));
    }

    @GetMapping("/bank/{id}")
    public ResponseEntity<BankAccountResponse> getBankAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.getBankAccount(id, user.getUsername()));
    }

    @DeleteMapping("/bank/{id}")
    public ResponseEntity<BankAccountResponse> closeBankAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.closeBankAccount(id, user.getUsername()));
    }

    @PostMapping("/bank/deposit")
    public ResponseEntity<BankAccountResponse> deposit(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(accountService.deposit(user.getUsername(), request));
    }

    @PostMapping("/bank/withdraw")
    public ResponseEntity<BankAccountResponse> withdraw(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(accountService.withdraw(user.getUsername(), request));
    }

    @PostMapping("/bank/transfer")
    public ResponseEntity<SuccessResponse> transfer(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody TransferRequest request) {
        accountService.transfer(user.getUsername(), request);
        return ResponseEntity.ok(SuccessResponse.of("Transfer completed"));
    }

    @GetMapping("/bank/{id}/statement")
    public ResponseEntity<List<CashOperationResponse>> getStatement(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.getAccountStatement(id, user.getUsername()));
    }

    // -------------------------------------------------------
    //  Trading accounts
    // -------------------------------------------------------

    @PostMapping("/trading")
    public ResponseEntity<TradingAccountResponse> openTradingAccount(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CreateTradingAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.openTradingAccount(user.getUsername(), request));
    }

    @GetMapping("/trading")
    public ResponseEntity<List<TradingAccountResponse>> getTradingAccounts(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.getTradingAccounts(user.getUsername()));
    }

    @GetMapping("/trading/{id}")
    public ResponseEntity<TradingAccountResponse> getTradingAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.getTradingAccount(id, user.getUsername()));
    }

    @PostMapping("/trading/fund")
    public ResponseEntity<SuccessResponse> fundTradingAccount(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody FundTradingAccountRequest request) {
        accountService.fundTradingAccount(user.getUsername(), request);
        return ResponseEntity.ok(SuccessResponse.of("Trading account funded"));
    }
}
