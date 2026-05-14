package org.example.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ApiResponse {

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user
    ) {
        public static AuthResponse of(String access, String refresh, UserResponse user) {
            return new AuthResponse(access, refresh, "Bearer", 86400, user);
        }
    }

    public record UserResponse(
            Long id,
            String username,
            String email,
            Boolean isActive,
            Boolean isLocked,
            List<String> roles,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt
    ) {}

    public record ProfileResponse(
            Long id,
            Long userId,
            String firstName,
            String lastName,
            String phone,
            String address,
            java.time.LocalDate dateOfBirth,
            String kycStatus
    ) {}

    public record BankAccountResponse(
            Long id,
            String accountNumber,
            String currency,
            BigDecimal balance,
            String status,
            LocalDateTime createdAt
    ) {}

    public record TradingAccountResponse(
            Long id,
            String accountNumber,
            String currency,
            BigDecimal cashBalance,
            BigDecimal frozenBalance,
            BigDecimal availableBalance,
            String status,
            LocalDateTime createdAt
    ) {}

    public record OrderResponse(
            String id,
            String ticker,
            String side,
            String orderType,
            BigDecimal price,
            Integer quantity,
            Integer remainingQty,
            Integer filledQty,
            String status,
            String timeInForce,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record TradeResponse(
            String id,
            String ticker,
            String buyOrderId,
            String sellOrderId,
            BigDecimal price,
            Integer quantity,
            BigDecimal totalValue,
            LocalDateTime tradedAt,
            String buyerUsername,
            String sellerUsername
    ) {}

    public record CashOperationResponse(
            Long id,
            String accountNumber,
            String operationType,
            BigDecimal amount,
            String currency,
            String status,
            String description,
            LocalDateTime createdAt
    ) {}

    public record OrderBookLevelResponse(BigDecimal price, int totalQty, int orderCount) {}

    public record OrderBookResponse(
            String ticker,
            List<OrderBookLevelResponse> bids,
            List<OrderBookLevelResponse> asks,
            BigDecimal spread,
            LocalDateTime timestamp
    ) {}

    public record InstrumentResponse(
            Long id,
            String ticker,
            String name,
            String instrumentType,
            String currency,
            Integer lotSize,
            BigDecimal tickSize,
            Boolean isActive
    ) {}

    public record NotificationResponse(
            Long id,
            String title,
            String message,
            String type,
            Boolean isRead,
            LocalDateTime createdAt
    ) {}

    public record PositionResponse(
            Long instrumentId,
            String ticker,
            String instrumentName,
            Integer quantity,
            Integer frozenQuantity,
            Integer availableQty,
            BigDecimal avgCost,
            LocalDateTime updatedAt
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean last
    ) {}

    public record ErrorResponse(
            int status,
            String error,
            String message,
            LocalDateTime timestamp
    ) {
        public static ErrorResponse of(int status, String error, String message) {
            return new ErrorResponse(status, error, message, LocalDateTime.now());
        }
    }

    public record SuccessResponse(String message) {
        public static SuccessResponse of(String message) { return new SuccessResponse(message); }
    }

    public record AuditLogResponse(
            Long id,
            String username,
            String action,
            String entityType,
            String entityId,
            String details,
            String result,
            LocalDateTime createdAt
    ) {}
}
