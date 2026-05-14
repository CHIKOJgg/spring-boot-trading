package org.example.domain.service;

import org.example.domain.model.Order;
import org.example.domain.model.TradeResult;
import org.example.domain.model.enums.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MatchingEngine Concurrency & Stress Tests")
class MatchingEngineConcurrencyTest {

    @Test
    @DisplayName("1000 concurrent orders maintain data integrity")
    void concurrentOrders_noDataCorruption() throws Exception {
        MatchingEngine engine = new MatchingEngine();
        int threadCount = 10;
        int ordersPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger tradeCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < ordersPerThread; i++) {
                        Side side = (i % 2 == 0) ? Side.BUY : Side.SELL;
                        // prices overlap to force matching
                        BigDecimal price = new BigDecimal(95 + (i % 10));
                        Order order = new Order.Builder()
                                .addUserId("user" + tid)
                                .addTicker("SBER")
                                .addSide(side)
                                .addPrice(price)
                                .addQuantity(1 + (i % 5))
                                .build();
                        List<TradeResult> trades = engine.placeLimitOrder(order);
                        tradeCount.addAndGet(trades.size());
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(errors).isEmpty();

        // verify book integrity — all quantities must be positive
        OrderBook book = engine.getOrCreateBook("SBER");
        book.getBids().forEach((price, queue) -> {
            queue.forEach(o -> assertThat(o.getQuantity()).isPositive());
        });
        book.getAsks().forEach((price, queue) -> {
            queue.forEach(o -> assertThat(o.getQuantity()).isPositive());
        });
    }

    @Test
    @DisplayName("Scenario: full fill chain — 10 bids consumed by 1 large sell")
    void largeSellConsumesManyBids() {
        MatchingEngine engine = new MatchingEngine();

        // add 10 buy orders of qty 10 at price 100
        for (int i = 0; i < 10; i++) {
            engine.placeLimitOrder(new Order.Builder()
                    .addUserId("buyer" + i).addTicker("YNDX")
                    .addSide(Side.BUY).addPrice(new BigDecimal("100")).addQuantity(10).build());
        }

        // one big sell that should consume all of them
        Order bigSell = new Order.Builder()
                .addUserId("bigSeller").addTicker("YNDX")
                .addSide(Side.SELL).addPrice(new BigDecimal("100")).addQuantity(100).build();

        List<TradeResult> trades = engine.placeLimitOrder(bigSell);

        int totalFilled = trades.stream().mapToInt(TradeResult::quantity).sum();
        assertThat(totalFilled).isEqualTo(100);
        assertThat(engine.getOrCreateBook("YNDX").getBestBid()).isNull();
        assertThat(engine.getOrCreateBook("YNDX").getBestAsk()).isNull();
    }

    @Test
    @DisplayName("Scenario: interleaved partial fills then cancel remainder")
    void partialFillThenCancel() {
        MatchingEngine engine = new MatchingEngine();

        Order sell = new Order.Builder().addId("sell-1").addUserId("s1")
                .addTicker("GAZP").addSide(Side.SELL).addPrice(new BigDecimal("200")).addQuantity(100).build();
        engine.placeLimitOrder(sell);

        // 3 partial buys
        for (int i = 0; i < 3; i++) {
            engine.placeLimitOrder(new Order.Builder()
                    .addUserId("buyer" + i).addTicker("GAZP")
                    .addSide(Side.BUY).addPrice(new BigDecimal("200")).addQuantity(20).build());
        }

        assertThat(sell.getQuantity()).isEqualTo(40); // 100 - 60

        boolean cancelled = engine.cancelOrder("GAZP", "sell-1");
        assertThat(cancelled).isTrue();
        assertThat(engine.getOrCreateBook("GAZP").getBestAsk()).isNull();
    }
}
