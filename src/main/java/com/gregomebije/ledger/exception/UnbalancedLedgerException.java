package com.gregomebije.ledger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;


@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class UnbalancedLedgerException extends RuntimeException {
    
    public UnbalancedLedgerException(String message) {
        super(message);
    }
}
