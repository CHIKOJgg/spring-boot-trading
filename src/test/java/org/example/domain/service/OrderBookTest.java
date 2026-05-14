package org.example.domain.service;

import org.example.domain.model.Order;
import org.example.domain.model.enums.OrderStatus;
import org.example.domain.model.enums.Side;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderBook Tests")
class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() { book = new OrderBook("SBER"); }

    @Test
    @DisplayName("BestBid returns highest price")
    void bestBid_highestPrice() {
        book.addOrder(order(Side.BUY, "100", 10));
        book.addOrder(order(Side.BUY, "105", 10));
        book.addOrder(order(Side.BUY, "98",  10));
        assertThat(book.getBestBid().getKey()).isEqualByComparingTo("105");
    }

    @Test
    @DisplayName("BestAsk returns lowest price")
    void bestAsk_lowestPrice() {
        book.addOrder(order(Side.SELL, "110", 10));
        book.addOrder(order(Side.SELL, "105", 10));
        book.addOrder(order(Side.SELL, "120", 10));
        assertThat(book.getBestAsk().getKey()).isEqualByComparingTo("105");
    }

    @Test
    @DisplayName("Empty book returns null for best bid/ask")
    void emptyBook_nullBestBidAsk() {
        assertThat(book.getBestBid()).isNull();
        assertThat(book.getBestAsk()).isNull();
    }

    @Test
    @DisplayName("Cancel only sets CANCELLED on the correct order, not others at same level")
    void cancelOrder_onlyTargetCancelled() {
        Order o1 = orderWithId("o1", Side.BUY, "100", 10);
        Order o2 = orderWithId("o2", Side.BUY, "100", 20);
        book.addOrder(o1);
        book.addOrder(o2);

        book.cancelOrder("o1");

        assertThat(o1.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o2.getStatus()).isEqualTo(OrderStatus.PENDING); // NOT affected
        assertThat(book.getBestBid()).isNotNull();  // level still exists
        assertThat(book.getBestBid().getValue()).hasSize(1);
    }

    @Test
    @DisplayName("Cancelling last order at a level removes that level")
    void cancelLastOrder_levelRemoved() {
        Order o = orderWithId("o1", Side.BUY, "100", 10);
        book.addOrder(o);
        book.cancelOrder("o1");
        assertThat(book.getBestBid()).isNull();
    }

    @Test
    @DisplayName("getTotalQuantityAtLevel sums all orders at price")
    void totalQuantityAtLevel() {
        book.addOrder(order(Side.BUY, "100", 30));
        book.addOrder(order(Side.BUY, "100", 20));
        book.addOrder(order(Side.BUY, "100", 10));
        assertThat(book.getTotalQuantityAtLevel(new BigDecimal("100"), Side.BUY)).isEqualTo(60);
    }

    @Test
    @DisplayName("Spread is absent when book has only bids or only asks")
    void spread_onlyBids_absent() {
        book.addOrder(order(Side.BUY, "100", 10));
        assertThat(book.getBidAskSpread()).isEmpty();
    }

    @Test
    @DisplayName("OrderCount reflects active orders")
    void orderCount() {
        book.addOrder(orderWithId("a", Side.BUY,  "100", 10));
        book.addOrder(orderWithId("b", Side.SELL, "110", 10));
        assertThat(book.getOrderCount()).isEqualTo(2);
        book.cancelOrder("a");
        assertThat(book.getOrderCount()).isEqualTo(1);
    }

    // -------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------

    private Order order(Side side, String price, int qty) {
        return new Order.Builder().addTicker("SBER")
                .addSide(side).addPrice(new BigDecimal(price)).addQuantity(qty).build();
    }

    private Order orderWithId(String id, Side side, String price, int qty) {
        return new Order.Builder().addId(id).addTicker("SBER")
                .addSide(side).addPrice(new BigDecimal(price)).addQuantity(qty).build();
    }
}
