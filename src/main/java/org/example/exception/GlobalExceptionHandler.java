package org.example.exception;

import org.example.dto.response.ApiResponse.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------
    //  Custom domain exceptions
    // -------------------------------------------------------

    public static class TradingException extends RuntimeException {
        public TradingException(String msg) { super(msg); }
    }
    public static class InsufficientFundsException extends TradingException {
        public InsufficientFundsException(String msg) { super(msg); }
    }
    public static class OrderNotFoundException extends TradingException {
        public OrderNotFoundException(String id) { super("Order not found: " + id); }
    }
    public static class AccountNotFoundException extends TradingException {
        public AccountNotFoundException(String msg) { super(msg); }
    }
    public static class InstrumentNotFoundException extends TradingException {
        public InstrumentNotFoundException(String ticker) { super("Instrument not found: " + ticker); }
    }
    public static class UserNotFoundException extends TradingException {
        public UserNotFoundException(String msg) { super(msg); }
    }
    public static class DuplicateResourceException extends TradingException {
        public DuplicateResourceException(String msg) { super(msg); }
    }
    public static class RiskLimitExceededException extends TradingException {
        public RiskLimitExceededException(String msg) { super(msg); }
    }
    public static class AccountFrozenException extends TradingException {
        public AccountFrozenException(String msg) { super(msg); }
    }

    // -------------------------------------------------------
    //  Handlers
    // -------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Validation failed", msg));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized", "Invalid username or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", "Access denied"));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "Insufficient funds", ex.getMessage()));
    }

    @ExceptionHandler({OrderNotFoundException.class, AccountNotFoundException.class,
            InstrumentNotFoundException.class, UserNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(TradingException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler({RiskLimitExceededException.class, AccountFrozenException.class})
    public ResponseEntity<ErrorResponse> handleRisk(TradingException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "Business rule violation", ex.getMessage()));
    }

    @ExceptionHandler(TradingException.class)
    public ResponseEntity<ErrorResponse> handleTrading(TradingException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", ex.getMessage()));
    }

    /**
     * BUG FIX: NoResourceFoundException (e.g. missing favicon.ico or any
     * unknown static path) was falling through to the catch-all Exception handler
     * below and being logged as ERROR with a full stack trace on every browser
     * request.  It is simply a 404 — log at DEBUG and return cleanly.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500, "Internal server error", "An unexpected error occurred"));
    }
}
