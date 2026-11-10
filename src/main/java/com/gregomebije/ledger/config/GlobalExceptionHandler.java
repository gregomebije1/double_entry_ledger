package com.gregomebije.ledger.config;

import com.gregomebije.ledger.exception.UnbalancedLedgerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //an unbalanced posting is the caller's fault.
    @ExceptionHandler(UnbalancedLedgerException.class)
    public ResponseEntity<Object> handleUnbalancedLedgerException(UnbalancedLedgerException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Bad credentials, duplicate email/tenant, unknown or foreign-tenant account, invalid account type
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Idempotency key already used
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<Object> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", message);
        body.put("status", status.value());
        return new ResponseEntity<>(body, status);
    }
}
