package org.example.domain.service;

import org.example.domain.model.Order;
import org.example.domain.model.TradeResult;
import org.example.domain.model.enums.OrderStatus;
import org.example.domain.model.enums.Side;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MatchingEngine Tests")
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() { engine = new MatchingEngine(); }

    // -------------------------------------------------------
    //  Full fill
    // -------------------------------------------------------

    @Test
    @DisplayName("BUY fully fills against matching SELL at same price")
    void fullFill_buySell_samePrice() {
        Order sell = order("seller", Side.SELL, "100", 50);
        Order buy  = order("buyer",  Side.BUY,  "100", 50);

        engine.placeLimitOrder(sell);
        List<TradeResult> trades = engine.placeLimitOrder(buy);

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualTo(50);
        assertThat(trades.get(0).price()).isEqualByComparingTo("100");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(engine.getOrCreateBook("SBER").getOrderCount()).isZero();
    }

    // -------------------------------------------------------
    //  Partial fill
    // -------------------------------------------------------

    @Test
    @DisplayName("BUY partially fills — remainder stays in book")
    void partialFill_buyLargerThanSell() {
        Order sell = order("seller", Side.SELL, "100", 30);
        Order buy  = order("buyer",  Side.BUY,  "100", 50);

        engine.placeLimitOrder(sell);
        List<TradeResult> trades = engine.placeLimitOrder(buy);

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualTo(30);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(buy.getQuantity()).isEqualTo(20);   // 50 - 30 remaining

        OrderBook book = engine.getOrCreateBook("SBER");
        assertThat(book.getBestBid()).isNotNull();
        assertThat(book.getBestBid().getValue().peek().getQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("SELL partially fills against multiple BID levels")
    void partialFill_sellAcrossMultipleBidLevels() {
        engine.placeLimitOrder(order("b1", Side.BUY, "102", 20));
        engine.placeLimitOrder(order("b2", Side.BUY, "101", 20));
        engine.placeLimitOrder(order("b3", Side.BUY, "100", 20));

        Order sell = order("seller", Side.SELL, "100", 50);
        List<TradeResult> trades = engine.placeLimitOrder(sell);

        assertThat(trades).hasSize(3);  // matches 20+20+10 across 3 levels
        int totalFilled = trades.stream().mapToInt(TradeResult::quantity).sum();
        assertThat(totalFilled).isEqualTo(50);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    // -------------------------------------------------------
    //  No match — order rests in book
    // -------------------------------------------------------

    @Test
    @DisplayName("BUY with price below best ASK rests in book")
    void noMatch_buyBelowAsk() {
        Order sell = order("seller", Side.SELL, "110", 50);
        Order buy  = order("buyer",  Side.BUY,  "100", 50);

        engine.placeLimitOrder(sell);
        List<TradeResult> trades = engine.placeLimitOrder(buy);

        assertThat(trades).isEmpty();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PENDING);
        OrderBook book = engine.getOrCreateBook("SBER");
        assertThat(book.getBestBid().getKey()).isEqualByComparingTo("100");
        assertThat(book.getBestAsk().getKey()).isEqualByComparingTo("110");
    }

    // -------------------------------------------------------
    //  Price-time priority (FIFO within level)
    // -------------------------------------------------------

    @Test
    @DisplayName("Orders at same price matched in FIFO order")
    void priceTimePriority_fifoWithinLevel() {
        Order sell1 = orderWithId("s1", "seller1", Side.SELL, "100", 10);
        Order sell2 = orderWithId("s2", "seller2", Side.SELL, "100", 10);
        engine.placeLimitOrder(sell1);
        engine.placeLimitOrder(sell2);

        Order buy = order("buyer", Side.BUY, "100", 10);
        List<TradeResult> trades = engine.placeLimitOrder(buy);

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).sellOrderId()).isEqualTo("s1"); // first in wins
        assertThat(sell1.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(sell2.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // -------------------------------------------------------
    //  Self-match prevention
    // -------------------------------------------------------

    @Test
    @DisplayName("Self-match is prevented — same userId cannot trade against itself")
    void selfMatchPrevention() {
        Order sell = order("sameUser", Side.SELL, "100", 50);
        Order buy  = order("sameUser", Side.BUY,  "100", 50);

        engine.placeLimitOrder(sell);
        List<TradeResult> trades = engine.placeLimitOrder(buy);

        assertThat(trades).isEmpty();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // -------------------------------------------------------
    //  Cancel
    // -------------------------------------------------------

    @Test
    @DisplayName("Cancelled order is removed from book")
    void cancelOrder_removedFromBook() {
        Order buy = orderWithId("b1", "buyer", Side.BUY, "100", 50);
        engine.placeLimitOrder(buy);

        assertThat(engine.getOrCreateBook("SBER").getBestBid()).isNotNull();

        boolean cancelled = engine.cancelOrder("SBER", "b1");

        assertThat(cancelled).isTrue();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(engine.getOrCreateBook("SBER").getBestBid()).isNull();
    }

    @Test
    @DisplayName("Cancelling non-existent order returns false")
    void cancelOrder_nonExistent_returnsFalse() {
        assertThat(engine.cancelOrder("SBER", "nonexistent-id")).isFalse();
    }

    // -------------------------------------------------------
    //  Spread
    // -------------------------------------------------------

    @Test
    @DisplayName("Bid-ask spread calculated correctly")
    void bidAskSpread() {
        engine.placeLimitOrder(order("b", Side.BUY,  "98", 10));
        engine.placeLimitOrder(order("s", Side.SELL, "102", 10));

        var spread = engine.getOrCreateBook("SBER").getBidAskSpread();
        assertThat(spread).isPresent();
        assertThat(spread.get()).isEqualByComparingTo("4");
    }

    // -------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------

    private Order order(String userId, Side side, String price, int qty) {
        return new Order.Builder()
                .addUserId(userId).addTicker("SBER")
                .addSide(side).addPrice(new BigDecimal(price)).addQuantity(qty)
                .build();
    }

    private Order orderWithId(String id, String userId, Side side, String price, int qty) {
        return new Order.Builder()
                .addId(id).addUserId(userId).addTicker("SBER")
                .addSide(side).addPrice(new BigDecimal(price)).addQuantity(qty)
                .build();
    }
}
