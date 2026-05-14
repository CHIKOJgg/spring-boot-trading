package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.request.OrderRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(user.getUsername(), request));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(user.getUsername(), orderId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(orderService.getActiveOrders(user.getUsername()));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<OrderResponse>> getOrderHistory(
            @AuthenticationPrincipal UserDetails user,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrderHistory(user.getUsername(), pageable));
    }

    @GetMapping("/trades")
    public ResponseEntity<Page<TradeResponse>> getTradeHistory(
            @AuthenticationPrincipal UserDetails user,
            @PageableDefault(size = 20, sort = "tradedAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getTradeHistory(user.getUsername(), pageable));
    }

    @GetMapping("/positions/{tradingAccountId}")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long tradingAccountId) {
        return ResponseEntity.ok(orderService.getPositions(user.getUsername(), tradingAccountId));
    }
}
