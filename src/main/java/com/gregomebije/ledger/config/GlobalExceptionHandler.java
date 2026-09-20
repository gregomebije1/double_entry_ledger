package com.gregomebije.ledger.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.gregomebije.ledger.exception.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnbalancedLedgerException.class)
    public ResponseEntity<Object> handleUnbalancedLedgerException(UnbalancedLedgerException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.NOT_FOUND.value());

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}
