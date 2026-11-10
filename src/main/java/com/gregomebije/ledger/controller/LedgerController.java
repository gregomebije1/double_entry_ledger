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
    @PostMapping("/ledgers/accounts")
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

    @PostMapping("/ledgers/transactions")
    public ResponseEntity<String> postTransaction(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateTransactionRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String txId = ledgerService.postTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("Ledger entry balance block recorded successfully: " + txId);
    }

    
}
