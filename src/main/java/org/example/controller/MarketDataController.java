package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.response.ApiResponse.*;
import org.example.service.CurrencyConversionService;
import org.example.service.DemoMarketService;
import org.example.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final CurrencyConversionService currencyConversionService;
    private final DemoMarketService demoMarketService;

    @GetMapping("/instruments")
    public ResponseEntity<List<InstrumentResponse>> getInstruments() {
        return ResponseEntity.ok(marketDataService.getActiveInstruments());
    }

    @GetMapping("/instruments/{ticker}")
    public ResponseEntity<InstrumentResponse> getInstrument(@PathVariable String ticker) {
        return ResponseEntity.ok(marketDataService.getInstrument(ticker));
    }

    @GetMapping("/orderbook/{ticker}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "20") int depth) {
        return ResponseEntity.ok(marketDataService.getOrderBook(ticker, depth));
    }

    @GetMapping("/trades/{ticker}")
    public ResponseEntity<List<TradeResponse>> getRecentTrades(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(marketDataService.getRecentTrades(ticker, limit));
    }

    @GetMapping("/chart/{ticker}")
    public ResponseEntity<InstrumentChartResponse> getPriceChart(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "60") int points) {
        return ResponseEntity.ok(marketDataService.getPriceChart(ticker, points));
    }

    @GetMapping("/currency/rates")
    public ResponseEntity<Map<String, BigDecimal>> getCurrencyRates() {
        return ResponseEntity.ok(currencyConversionService.getRates());
    }

    @GetMapping("/currency/convert")
    public ResponseEntity<CurrencyConversionResponse> convertCurrency(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(currencyConversionService.convert(amount, from, to));
    }

    @GetMapping("/demo/pulse")
    public ResponseEntity<DemoMarketPulseResponse> getDemoPulse() {
        return ResponseEntity.ok(demoMarketService.getPulse());
    }

    @GetMapping("/demo/trades")
    public ResponseEntity<List<TradeResponse>> getDemoTrades(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(demoMarketService.getRecentTrades(limit));
    }
}
