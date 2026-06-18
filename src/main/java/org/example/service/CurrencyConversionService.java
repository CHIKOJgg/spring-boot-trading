package org.example.service;

import org.example.dto.response.ApiResponse.CurrencyConversionResponse;
import org.example.exception.GlobalExceptionHandler.TradingException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class CurrencyConversionService {

    private static final Map<String, BigDecimal> TO_BYN = Map.ofEntries(
            Map.entry("BYN", new BigDecimal("1.0000")),
            Map.entry("USD", new BigDecimal("3.2500")),
            Map.entry("EUR", new BigDecimal("3.5500")),
            Map.entry("RUB", new BigDecimal("0.0360"))
    );

    public Map<String, BigDecimal> getRates() {
        return new LinkedHashMap<>(TO_BYN);
    }

    public CurrencyConversionResponse convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);

        BigDecimal fromRate = rateToByn(from);
        BigDecimal toRate = rateToByn(to);
        BigDecimal bynAmount = amount.multiply(fromRate);
        BigDecimal converted = bynAmount.divide(toRate, 4, RoundingMode.HALF_UP);
        BigDecimal rate = fromRate.divide(toRate, 6, RoundingMode.HALF_UP);

        return new CurrencyConversionResponse(
                amount.setScale(4, RoundingMode.HALF_UP),
                from,
                to,
                rate,
                converted
        );
    }

    public BigDecimal toByn(BigDecimal amount, String currency) {
        return amount.multiply(rateToByn(normalize(currency)));
    }

    private BigDecimal rateToByn(String currency) {
        BigDecimal rate = TO_BYN.get(currency);
        if (rate == null) {
            throw new TradingException("Unsupported currency: " + currency);
        }
        return rate;
    }

    private String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new TradingException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
