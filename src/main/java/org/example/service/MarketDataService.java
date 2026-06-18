package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Order;
import org.example.domain.model.entity.InstrumentEntity;
import org.example.domain.model.entity.TradeEntity;
import org.example.domain.service.MatchingEngine;
import org.example.domain.service.OrderBook;
import org.example.dto.response.ApiResponse.*;
import org.example.exception.GlobalExceptionHandler.InstrumentNotFoundException;
import org.example.repository.InstrumentRepository;
import org.example.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    @Transactional(readOnly = true)
    public InstrumentChartResponse getPriceChart(String ticker, int points) {
        InstrumentEntity instrument = instrumentRepository.findByTicker(ticker)
                .orElseThrow(() -> new InstrumentNotFoundException(ticker));

        int limit = Math.max(1, Math.min(points, 120));
        List<ChartPointResponse> series = tradeRepository.findTop50ByInstrumentTickerOrderByTradedAtDesc(ticker)
                .stream()
                .limit(limit)
                .sorted(Comparator.comparing(TradeEntity::getTradedAt))
                .map(t -> new ChartPointResponse(t.getTradedAt(), t.getPrice(), t.getQuantity()))
                .toList();

        if (series.isEmpty()) {
            series = buildSyntheticSeries(instrument, limit);
        }

        BigDecimal open = series.get(0).price();
        BigDecimal close = series.get(series.size() - 1).price();
        BigDecimal high = series.stream().map(ChartPointResponse::price).max(BigDecimal::compareTo).orElse(close);
        BigDecimal low = series.stream().map(ChartPointResponse::price).min(BigDecimal::compareTo).orElse(open);
        BigDecimal change = close.subtract(open);
        BigDecimal changePercent = open.signum() == 0
                ? BigDecimal.ZERO
                : change.divide(open, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        return new InstrumentChartResponse(
                instrument.getTicker(),
                instrument.getName(),
                instrument.getInstrumentType(),
                instrument.getCurrency(),
                open,
                close,
                high,
                low,
                change,
                changePercent,
                series,
                LocalDateTime.now());
    }

    private InstrumentResponse toInstrumentResponse(org.example.domain.model.entity.InstrumentEntity i) {
        return new InstrumentResponse(i.getId(), i.getTicker(), i.getName(),
                i.getInstrumentType(), i.getCurrency(), i.getLotSize(), i.getTickSize(), i.getIsActive());
    }

    private List<ChartPointResponse> buildSyntheticSeries(InstrumentEntity instrument, int points) {
        BigDecimal anchor = defaultAnchorPrice(instrument);
        double phase = Math.abs(instrument.getTicker().hashCode() % 360) / 57.29577951308232d;
        LocalDateTime start = LocalDateTime.now().minusMinutes((long) points * 5L);
        List<ChartPointResponse> series = new ArrayList<>(points);

        for (int i = 0; i < points; i++) {
            double wave = Math.sin(phase + i * 0.33d) * 0.025d + Math.cos(phase / 2.0d + i * 0.11d) * 0.012d;
            BigDecimal drift = BigDecimal.valueOf(1.0d + (i * 0.0015d));
            BigDecimal multiplier = BigDecimal.valueOf(1.0d + wave);
            BigDecimal price = anchor.multiply(drift)
                    .multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
            if (price.compareTo(new BigDecimal("0.01")) < 0) {
                price = new BigDecimal("0.01");
            }
            int volume = 100 + Math.abs((instrument.getTicker().hashCode() + i * 37) % 900);
            series.add(new ChartPointResponse(start.plusMinutes((long) i * 5L), price, volume));
        }

        return series;
    }

    private BigDecimal defaultAnchorPrice(InstrumentEntity instrument) {
        int seed = Math.abs(instrument.getTicker().hashCode());
        if ("BOND".equalsIgnoreCase(instrument.getInstrumentType())) {
            return BigDecimal.valueOf(95 + (seed % 30) * 0.25d).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(20 + (seed % 600) / 4.0d).setScale(2, RoundingMode.HALF_UP);
    }
}
