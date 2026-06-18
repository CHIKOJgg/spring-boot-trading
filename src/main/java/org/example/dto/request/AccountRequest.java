package org.example.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class AccountRequest {

    public record CreateBankAccountRequest(
            @NotBlank @Size(max = 10) String currency
    ) {
        public CreateBankAccountRequest { if (currency == null) currency = "BYN"; }
    }

    public record DepositRequest(
            @NotNull Long bankAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String description
    ) {}

    public record WithdrawRequest(
            @NotNull Long bankAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String description
    ) {}

    public record TransferRequest(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String description
    ) {}

    public record CreateTradingAccountRequest(
            @NotBlank @Size(max = 10) String currency
    ) {
        public CreateTradingAccountRequest { if (currency == null) currency = "BYN"; }
    }

    public record FundTradingAccountRequest(
            @NotNull Long bankAccountId,
            @NotNull Long tradingAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}
}
