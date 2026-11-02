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
    @PostMapping("/onboarding/register")
    public ResponseEntity<String> registerTenant(@RequestBody TenantRegistrationDto dto) {
        onboardingService.registerTenant(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Tenant organization enrolled successfully");    
    }

    @PostMapping("/onboarding/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto dto) {
        String token = onboardingService.login(dto);
        AuthResponseDto response = new AuthResponseDto(token);
        return ResponseEntity.ok(response);
    }

    // --- Authenticated routing ---
    @PostMapping("/ledgers/accounts")
    public ResponseEntity<String> createAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateAccountDto dto
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String accountId = ledgerService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Account created: " + accountId);
    }

    @PostMapping("/ledgers/transactions")
    public ResponseEntity<String> postTransaction(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateTransactionDto dto
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String txId = ledgerService.postTransaction(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Ledger entry balance block recorded successfully: " + txId);
    }

    
}
