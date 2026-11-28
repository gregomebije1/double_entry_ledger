package com.gregomebije.ledger.controller;

import com.gregomebije.ledger.service.*;
import com.gregomebije.ledger.dto.Models.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    private final OnboardingService onboardingService;
    private final LedgerService ledgerService;

    public LedgerController(OnboardingService onboardingService, LedgerService ledgerService) {
        this.onboardingService = onboardingService;
        this.ledgerService = ledgerService;
    }

    // --- Unauthenticated routing ---
    @PostMapping("/onboarding/user")
    public ResponseEntity<String> registerUser(@RequestBody CreateUserRequest request) {
        onboardingService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("User organization enrolled successfully");    
    }

    @PostMapping("/onboarding/tenant")
    public ResponseEntity<String> registerTenant(@RequestBody CreateTenantRequest request) {
        onboardingService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("Tenant added to user organization");    
    }

    @PostMapping("/onboarding/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = onboardingService.login(request);
        LoginResponse response = new LoginResponse(token);
        return ResponseEntity.ok(response);
    }

    // --- Authenticated routing ---
    @PostMapping("/ledger/accounts")
    public ResponseEntity<String> createAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateAccountRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String accountId = ledgerService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("Account created: " + accountId);
    }

    @PostMapping("/ledger/entries")
    public ResponseEntity<String> postLedger(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateLedgerRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String txId = ledgerService.postLedger(request);
        /*
        -- [Ledger DB]  (:'x' = value bound from the request body)
        BEGIN;
        WITH e AS (
        INSERT INTO journal_entries (id, book_id, memo)
        SELECT :'entry_id'::uuid, b.id, :'memo' FROM books b WHERE b.name = :'book'
        ON CONFLICT (id) DO NOTHING RETURNING id, book_id)
        INSERT INTO journal_lines (entry_id, account_id, sub_ref, debit, credit)
        SELECT e.id, a.id, l.sub_ref, COALESCE(l.debit, 0), COALESCE(l.credit, 0)
        FROM e CROSS JOIN jsonb_to_recordset(:'lines'::jsonb)
            AS l(account text, sub_ref uuid, debit bigint, credit bigint)
        JOIN accounts a ON a.book_id = e.book_id AND a.code = l.account;
        -- no entry row returned => replay of a finished call: ROLLBACK, return 200 with the original result
        -- inserted line count must equal lines sent, else unknown account: ROLLBACK, return 422
        COMMIT;   -- the deferred balance trigger from 7.4 rejects an unbalanced entry here
                */
        return ResponseEntity.status(HttpStatus.CREATED).body("Ledger entry balance block recorded successfully: " + txId);
    }

    /*
    GetMapping("/ledgers/balance")
    public ResponseEntity<String> postTransaction(
            
        - [Ledger DB]
        SELECT b.name AS book, a.code, l.sub_ref, l.debit, l.credit
        FROM journal_lines l JOIN accounts a ON a.id = l.account_id JOIN books b ON b.id = a.book_id
        WHERE l.entry_id IN (:e1, :e3) ORDER BY b.name, a.code;
    */
}
