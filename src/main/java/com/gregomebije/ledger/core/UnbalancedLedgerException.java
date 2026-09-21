package com.gregomebije.ledger.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;


@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class UnbalancedLedgerException extends RuntimeException {
    
    public UnbalancedLedgerException(String message) {
        super(message);
    }
}
