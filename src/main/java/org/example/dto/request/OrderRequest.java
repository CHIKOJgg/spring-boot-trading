package org.example.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class OrderRequest {

    public record PlaceOrderRequest(
            @NotBlank String ticker,
            @NotNull Long tradingAccountId,
            @NotBlank @Pattern(regexp = "BUY|SELL") String side,
            @NotNull @DecimalMin("0.0001") BigDecimal price,
            @NotNull @Min(1) Integer quantity,
            @Pattern(regexp = "GTC|IOC|FOK") String timeInForce
    ) {
        public PlaceOrderRequest {
            if (timeInForce == null) timeInForce = "GTC";
        }
    }

    public record CancelOrderRequest(@NotBlank String orderId) {}

    public record ModifyOrderRequest(
            @NotBlank String orderId,
            @DecimalMin("0.0001") BigDecimal newPrice,
            @Min(1) Integer newQuantity
    ) {}
}
