package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Order;
import org.example.domain.service.MatchingEngine;
import org.example.domain.service.OrderBook;
import org.example.dto.response.ApiResponse.*;
import org.example.exception.GlobalExceptionHandler.InstrumentNotFoundException;
import org.example.repository.InstrumentRepository;
import org.example.repository.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MatchingEngine matchingEngine;
    private final InstrumentRepository instrumentRepository;
    private final TradeRepository tradeRepository;

    public OrderBookResponse getOrderBook(String ticker, int depth) {
        instrumentRepository.findByTicker(ticker)
                .orElseThrow(() -> new InstrumentNotFoundException(ticker));

        OrderBook book = matchingEngine.getOrCreateBook(ticker);

        List<OrderBookLevelResponse> bids = book.getBids().entrySet().stream()
                .limit(depth)
                .map(e -> new OrderBookLevelResponse(
                        e.getKey(),
                        e.getValue().stream().mapToInt(Order::getQuantity).sum(),
                        e.getValue().size()))
                .toList();

        List<OrderBookLevelResponse> asks = book.getAsks().entrySet().stream()
                .limit(depth)
                .map(e -> new OrderBookLevelResponse(
                        e.getKey(),
                        e.getValue().stream().mapToInt(Order::getQuantity).sum(),
                        e.getValue().size()))
                .toList();

        BigDecimal spread = book.getBidAskSpread().orElse(null);
        return new OrderBookResponse(ticker, bids, asks, spread, LocalDateTime.now());
    }

    // @Cacheable removed: Redis deserialization failures caused empty instrument dropdowns.
    // Direct DB query on instruments table (<50 rows) adds under 1ms per request.
    @Transactional(readOnly = true)
    public List<InstrumentResponse> getActiveInstruments() {
        return instrumentRepository.findByIsActiveTrue()
                .stream().map(this::toInstrumentResponse).toList();
    }

    public InstrumentResponse getInstrument(String ticker) {
        return instrumentRepository.findByTicker(ticker)
                .map(this::toInstrumentResponse)
                .orElseThrow(() -> new InstrumentNotFoundException(ticker));
    }

    @Transactional(readOnly = true) // BUG FIX #8: LazyInitializationException risk without session
    public List<TradeResponse> getRecentTrades(String ticker, int limit) {
        return tradeRepository.findTop50ByInstrumentTickerOrderByTradedAtDesc(ticker)
                .stream().limit(limit).map(t -> new TradeResponse(
                        t.getId(), t.getInstrument().getTicker(),
                        t.getBuyOrder().getId(), t.getSellOrder().getId(),
                        t.getPrice(), t.getQuantity(), t.getTotalValue(),
                        t.getTradedAt(), t.getBuyer().getUsername(), t.getSeller().getUsername()))
                .toList();
    }

    private InstrumentResponse toInstrumentResponse(org.example.domain.model.entity.InstrumentEntity i) {
        return new InstrumentResponse(i.getId(), i.getTicker(), i.getName(),
                i.getInstrumentType(), i.getCurrency(), i.getLotSize(), i.getTickSize(), i.getIsActive());
    }
}
