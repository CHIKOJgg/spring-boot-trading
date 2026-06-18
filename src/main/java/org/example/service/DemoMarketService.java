package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.dto.response.ApiResponse.DemoMarketPulseResponse;
import org.example.dto.response.ApiResponse.TradeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Random;

@Service
public class DemoMarketService {

    private static final String TICKER = "DEMO-BYN";

    private final Random random = new Random();
    private final ArrayDeque<TradeResponse> recentTrades = new ArrayDeque<>();

    @Value("${demo-market.start-price:100.0000}")
    private BigDecimal startPrice;

    private BigDecimal openPrice;
    private BigDecimal lastPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal volume;
    private long sequence;

    @PostConstruct
    public synchronized void init() {
        openPrice = startPrice.setScale(4, RoundingMode.HALF_UP);
        lastPrice = openPrice;
        highPrice = openPrice;
        lowPrice = openPrice;
        volume = BigDecimal.ZERO;
        sequence = 0L;
        for (int i = 0; i < 8; i++) {
            tick();
        }
    }

    @Scheduled(fixedRateString = "${demo-market.tick-ms:2500}")
    public synchronized void tick() {
        BigDecimal move = BigDecimal.valueOf((random.nextDouble() - 0.5d) * 1.8d);
        BigDecimal nextPrice = lastPrice.add(move).setScale(4, RoundingMode.HALF_UP);
        if (nextPrice.compareTo(new BigDecimal("1.0000")) < 0) {
            nextPrice = new BigDecimal("1.0000");
        }

        int qty = 10 + random.nextInt(190);
        BigDecimal notional = nextPrice.multiply(BigDecimal.valueOf(qty)).setScale(4, RoundingMode.HALF_UP);
        long id = ++sequence;
        TradeResponse trade = new TradeResponse(
                "demo-trade-" + id,
                TICKER,
                "demo-buy-" + id,
                "demo-sell-" + id,
                nextPrice,
                qty,
                notional,
                LocalDateTime.now(),
                "SYSTEM_BUY",
                "SYSTEM_SELL"
        );

        recentTrades.addFirst(trade);
        while (recentTrades.size() > 40) {
            recentTrades.removeLast();
        }

        lastPrice = nextPrice;
        if (nextPrice.compareTo(highPrice) > 0) {
            highPrice = nextPrice;
        }
        if (nextPrice.compareTo(lowPrice) < 0) {
            lowPrice = nextPrice;
        }
        volume = volume.add(notional);
    }

    public synchronized DemoMarketPulseResponse getPulse() {
        BigDecimal change = lastPrice.subtract(openPrice).setScale(4, RoundingMode.HALF_UP);
        BigDecimal changePct = openPrice.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : change.divide(openPrice, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        return new DemoMarketPulseResponse(
                TICKER,
                lastPrice,
                openPrice,
                change,
                changePct.setScale(2, RoundingMode.HALF_UP),
                highPrice,
                lowPrice,
                volume.setScale(4, RoundingMode.HALF_UP),
                LocalDateTime.now()
        );
    }

    public synchronized List<TradeResponse> getRecentTrades(int limit) {
        return recentTrades.stream().limit(Math.max(0, limit)).toList();
    }
}
