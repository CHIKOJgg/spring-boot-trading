package org.example.domain.service;

import org.example.domain.model.Order;
import org.example.domain.model.enums.OrderStatus;
import org.example.domain.model.enums.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * In-memory order book for a single instrument.
 * Price-time priority: bids sorted highest-first, asks sorted lowest-first.
 * All public methods are synchronized for thread safety.
 */
public class OrderBook {

    private static final Logger log = LoggerFactory.getLogger(OrderBook.class);

    private final String ticker;

    // bids: highest price first
    private final NavigableMap<BigDecimal, ArrayDeque<Order>> bids =
            new TreeMap<>(Comparator.reverseOrder());

    // asks: lowest price first
    private final NavigableMap<BigDecimal, ArrayDeque<Order>> asks =
            new TreeMap<>();

    // fast lookup by orderId
    private final Map<String, Order> orderIndex = new HashMap<>();

    public OrderBook(String ticker) {
        this.ticker = ticker;
    }

    public String getTicker() { return ticker; }

    // -------------------------------------------------------
    //  Add / Cancel
    // -------------------------------------------------------

    public synchronized void addOrder(Order order) {
        orderIndex.put(order.getId(), order);
        if (order.getSideOfOrder() == Side.SELL) {
            asks.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        } else {
            bids.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        }
        log.debug("OrderBook[{}] added {} {} @{} qty={}", ticker,
                order.getSideOfOrder(), order.getId(), order.getPrice(), order.getQuantity());
    }

    /**
     * Cancel an order by id.
     * Status is set ONLY on the matching order, not every order at the price level.
     */
    public synchronized boolean cancelOrder(String orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) {
            log.warn("OrderBook[{}] cancelOrder: order {} not found", ticker, orderId);
            return false;
        }

        NavigableMap<BigDecimal, ArrayDeque<Order>> side =
                order.getSideOfOrder() == Side.BUY ? bids : asks;

        ArrayDeque<Order> level = side.get(order.getPrice());
        if (level == null) {
            return false;
        }

        boolean removed = level.removeIf(o -> {
            if (o.getId().equals(orderId)) {
                o.setStatus(OrderStatus.CANCELLED);   // only THIS order
                return true;
            }
            return false;
        });

        if (removed && level.isEmpty()) {
            side.remove(order.getPrice());
        }

        log.debug("OrderBook[{}] cancelled order {}", ticker, orderId);
        return removed;
    }

    /**
     * BUG FIX #3: New method to remove a fully-filled resting order from the index.
     * Previously MatchingEngine called book.getOrderIndex() (a no-op read) instead
     * of actually removing the filled order, causing the index to grow unboundedly
     * and getOrderCount() to return incorrect values.
     */
    public synchronized void removeFromIndex(String orderId) {
        orderIndex.remove(orderId);
        log.debug("OrderBook[{}] removed filled order {} from index", ticker, orderId);
    }

    // -------------------------------------------------------
    //  Best bid / ask
    // -------------------------------------------------------

    public synchronized Map.Entry<BigDecimal, ArrayDeque<Order>> getBestBid() {
        return bids.isEmpty() ? null : bids.firstEntry();
    }

    public synchronized Map.Entry<BigDecimal, ArrayDeque<Order>> getBestAsk() {
        return asks.isEmpty() ? null : asks.firstEntry();
    }

    // -------------------------------------------------------
    //  Empty-level cleanup
    // -------------------------------------------------------

    public synchronized void removeEmptyLevelBids(BigDecimal price) {
        ArrayDeque<Order> level = bids.get(price);
        if (level != null && level.isEmpty()) {
            bids.remove(price);
            log.debug("OrderBook[{}] removed empty bid level @{}", ticker, price);
        }
    }

    public synchronized void removeEmptyLevelAsks(BigDecimal price) {
        ArrayDeque<Order> level = asks.get(price);
        if (level != null && level.isEmpty()) {
            asks.remove(price);
            log.debug("OrderBook[{}] removed empty ask level @{}", ticker, price);
        }
    }

    // -------------------------------------------------------
    //  Read-only accessors for market data
    // -------------------------------------------------------

    public synchronized NavigableMap<BigDecimal, ArrayDeque<Order>> getBids() {
        return Collections.unmodifiableNavigableMap(bids);
    }

    public synchronized NavigableMap<BigDecimal, ArrayDeque<Order>> getAsks() {
        return Collections.unmodifiableNavigableMap(asks);
    }

    public synchronized Map<String, Order> getOrderIndex() {
        return Collections.unmodifiableMap(orderIndex);
    }

    public synchronized int getTotalQuantityAtLevel(BigDecimal price, Side side) {
        Map<BigDecimal, ArrayDeque<Order>> map = (side == Side.BUY) ? bids : asks;
        ArrayDeque<Order> level = map.get(price);
        if (level == null) return 0;
        return level.stream().mapToInt(Order::getQuantity).sum();
    }

    public synchronized Optional<BigDecimal> getBidAskSpread() {
        return Optional.ofNullable(getBestBid())
                .flatMap(bid -> Optional.ofNullable(getBestAsk())
                        .map(ask -> ask.getKey().subtract(bid.getKey())));
    }

    public synchronized int getOrderCount() {
        return orderIndex.size();
    }

    // -------------------------------------------------------
    //  Debug print
    // -------------------------------------------------------

    public synchronized String printBook() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== OrderBook [").append(ticker).append("] ===\n");
        sb.append("--- ASKS ---\n");
        new ArrayList<>(asks.keySet()).forEach(price ->
                sb.append("  ASK @").append(price)
                  .append(" | orders=").append(asks.get(price).size())
                  .append(" | qty=").append(getTotalQuantityAtLevel(price, Side.SELL)).append("\n"));
        sb.append("--- BIDS ---\n");
        new ArrayList<>(bids.keySet()).forEach(price ->
                sb.append("  BID @").append(price)
                  .append(" | orders=").append(bids.get(price).size())
                  .append(" | qty=").append(getTotalQuantityAtLevel(price, Side.BUY)).append("\n"));
        return sb.toString();
    }
}
