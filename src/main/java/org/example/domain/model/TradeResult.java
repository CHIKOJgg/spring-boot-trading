package org.example.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable record of a single execution produced by the matching engine.
 */
public record TradeResult(
        String buyOrderId,
        String sellOrderId,
        String instrumentTicker,
        BigDecimal price,
        int quantity,
        LocalDateTime tradedAt
) {
    public BigDecimal totalValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
