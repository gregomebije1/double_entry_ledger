package com.gregomebije.ledger.api;

import com.gregomebije.ledger.core.LedgerService;
import com.gregomebije.ledger.core.PostTransactionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledgers")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<Void> createAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateAccountRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        //TODO: Insert an account into the account table
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/transactions")
    public ResponseEntity<Void> postTransaction(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PostTransactionRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String tenant = authorization.substring("Bearer ".length());
        ledgerService.postTransaction(tenant, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record CreateAccountRequest(String account_id, String currency) {}
}
