package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.response.ApiResponse.*;
import org.example.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

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
}
