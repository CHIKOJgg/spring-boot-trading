package org.example.domain.service;

import org.example.domain.model.Order;
import org.example.domain.model.TradeResult;
import org.example.domain.model.enums.OrderStatus;
import org.example.domain.model.enums.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core matching engine.
 * Maintains one OrderBook per instrument ticker.
 * Uses price-time priority (FIFO within price level).
 * Self-match prevention: a user cannot trade against their own order.
 */
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    // -------------------------------------------------------
    //  Book management
    // -------------------------------------------------------

    public OrderBook getOrCreateBook(String ticker) {
        return books.computeIfAbsent(ticker, OrderBook::new);
    }

    public OrderBook getBook(String ticker) {
        return books.get(ticker);
    }

    public Map<String, OrderBook> getAllBooks() {
        return books;
    }

    // -------------------------------------------------------
    //  Place limit order — returns list of generated trades
    // -------------------------------------------------------

    public synchronized List<TradeResult> placeLimitOrder(Order incomingOrder) {
        String ticker = incomingOrder.getInstrumentTicker();
        OrderBook book = getOrCreateBook(ticker);

        List<TradeResult> trades = new ArrayList<>();

        int startQty     = incomingOrder.getQuantity();
        int remainingQty = startQty;

        if (incomingOrder.getSideOfOrder() == Side.BUY) {
            remainingQty = matchBuyOrder(incomingOrder, book, trades, remainingQty);
        } else {
            remainingQty = matchSellOrder(incomingOrder, book, trades, remainingQty);
        }

        // update incoming order status
        if (remainingQty == 0) {
            incomingOrder.setStatus(OrderStatus.FILLED);
            incomingOrder.setQuantity(0);
        } else {
            if (remainingQty < startQty) {
                incomingOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
            incomingOrder.setQuantity(remainingQty);
            book.addOrder(incomingOrder);   // rest goes into book
        }

        log.info("MatchingEngine[{}] order {} → {} trades, remaining={}",
                ticker, incomingOrder.getId(), trades.size(), remainingQty);

        return trades;
    }

    // -------------------------------------------------------
    //  Cancel
    //  BUG FIX #4: cancelOrder is now synchronized to prevent a race condition
    //  with concurrent placeLimitOrder calls on the same engine instance.
    //  Previously cancelOrder was unsynchronized while placeLimitOrder held
    //  the monitor, allowing a cancel to mutate an OrderBook mid-match.
    // -------------------------------------------------------

    public synchronized boolean cancelOrder(String ticker, String orderId) {
        OrderBook book = books.get(ticker);
        if (book == null) return false;
        return book.cancelOrder(orderId);
    }

    // -------------------------------------------------------
    //  Internal matching helpers
    // -------------------------------------------------------

    private int matchBuyOrder(Order buy, OrderBook book,
                              List<TradeResult> trades, int remainingQty) {
        Map.Entry<BigDecimal, ArrayDeque<Order>> bestAsk = book.getBestAsk();

        while (remainingQty > 0
                && bestAsk != null
                && bestAsk.getKey().compareTo(buy.getPrice()) <= 0) {

            ArrayDeque<Order> levelQueue = bestAsk.getValue();

            if (levelQueue == null || levelQueue.isEmpty()) {
                book.removeEmptyLevelAsks(bestAsk.getKey());
                bestAsk = book.getBestAsk();
                continue;
            }

            Order resting = levelQueue.peekFirst();
            if (resting == null) {
                book.removeEmptyLevelAsks(bestAsk.getKey());
                bestAsk = book.getBestAsk();
                continue;
            }

            // self-match prevention
            if (resting.getUserId().equals(buy.getUserId())) {
                log.warn("Self-match prevented: user {} order {} vs {}",
                        buy.getUserId(), buy.getId(), resting.getId());
                break;
            }

            int executed = Math.min(resting.getQuantity(), remainingQty);
            remainingQty -= executed;
            resting.setQuantity(resting.getQuantity() - executed);

            TradeResult trade = new TradeResult(
                    buy.getId(), resting.getId(),
                    buy.getInstrumentTicker(),
                    bestAsk.getKey(),
                    executed,
                    LocalDateTime.now()
            );
            trades.add(trade);

            log.debug("MATCH BUY {} SELL {} @{} qty={}", buy.getId(), resting.getId(),
                    bestAsk.getKey(), executed);

            if (resting.getQuantity() == 0) {
                resting.setStatus(OrderStatus.FILLED);
                levelQueue.pollFirst();
                // BUG FIX #3: was calling book.getOrderIndex() (a no-op read) instead of
                // actually removing the filled resting order from the index.
                // This caused orderIndex to grow unboundedly with filled orders
                // and getOrderCount() to return inflated values.
                book.removeFromIndex(resting.getId());
            }
            if (levelQueue.isEmpty()) {
                book.removeEmptyLevelAsks(bestAsk.getKey());
            }

            bestAsk = book.getBestAsk();
        }
        return remainingQty;
    }

    private int matchSellOrder(Order sell, OrderBook book,
                               List<TradeResult> trades, int remainingQty) {
        Map.Entry<BigDecimal, ArrayDeque<Order>> bestBid = book.getBestBid();

        while (remainingQty > 0
                && bestBid != null
                && bestBid.getKey().compareTo(sell.getPrice()) >= 0) {

            ArrayDeque<Order> levelQueue = bestBid.getValue();

            if (levelQueue == null || levelQueue.isEmpty()) {
                book.removeEmptyLevelBids(bestBid.getKey());
                bestBid = book.getBestBid();
                continue;
            }

            Order resting = levelQueue.peekFirst();
            if (resting == null) {
                book.removeEmptyLevelBids(bestBid.getKey());
                bestBid = book.getBestBid();
                continue;
            }

            // self-match prevention
            if (resting.getUserId().equals(sell.getUserId())) {
                log.warn("Self-match prevented: user {} order {} vs {}",
                        sell.getUserId(), sell.getId(), resting.getId());
                break;
            }

            int executed = Math.min(resting.getQuantity(), remainingQty);
            remainingQty -= executed;
            resting.setQuantity(resting.getQuantity() - executed);

            TradeResult trade = new TradeResult(
                    resting.getId(), sell.getId(),
                    sell.getInstrumentTicker(),
                    bestBid.getKey(),
                    executed,
                    LocalDateTime.now()
            );
            trades.add(trade);

            log.debug("MATCH BUY {} SELL {} @{} qty={}", resting.getId(), sell.getId(),
                    bestBid.getKey(), executed);

            if (resting.getQuantity() == 0) {
                resting.setStatus(OrderStatus.FILLED);
                levelQueue.pollFirst();
                // BUG FIX #3: same fix as in matchBuyOrder — remove filled resting order
                // from the index instead of performing the original no-op read.
                book.removeFromIndex(resting.getId());
            }
            if (levelQueue.isEmpty()) {
                book.removeEmptyLevelBids(bestBid.getKey());
            }

            bestBid = book.getBestBid();
        }
        return remainingQty;
    }
}
