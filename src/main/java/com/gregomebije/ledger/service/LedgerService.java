package com.gregomebije.ledger.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gregomebije.ledger.dto.Models.*;
import com.gregomebije.ledger.model.*;
import com.gregomebije.ledger.utility.LedgerValidator;
import com.gregomebije.ledger.utility.TenantContext;
import com.gregomebije.ledger.repository.AccountRepository;
import com.gregomebije.ledger.repository.LedgerTransactionRepository; 
import com.gregomebije.ledger.repository.TenantRepository;

@Service
public class LedgerService {

    private final LedgerValidator validator;
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final TenantRepository tenantRepository;

    public LedgerService(LedgerValidator validator, 
        TenantRepository tenantRepository,
        AccountRepository accountRepository, 
        LedgerTransactionRepository transactionRepository) {
        this.validator = validator;
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public String createAccount(CreateAccountDto dto) {
        String currentTenant = TenantContext.getTenantId(); // Gather explicitly from request context container

        Tenant tenant = tenantRepository.findById(currentTenant).get();
        Account account = Account.builder()
                .tenant(tenant) // Set explicitly
                .accountNumber(dto.accountNumber())
                .name(dto.name())
                .type(AccountType.valueOf(dto.type().toUpperCase()))
                .currency(dto.currency())
                .build();
        Account savedAccount = accountRepository.save(account);

        //Todo: Handle if it already exists and idempotency
        return savedAccount.getId().toString();
    }

    @Transactional
    public String postTransaction(CreateTransactionDto dto) {
        validator.validate(dto.lines());
        String currentTenant = TenantContext.getTenantId(); // Gather explicitly from request context container
        Tenant tenant = tenantRepository.findById(currentTenant).get();
       

        if (transactionRepository.existsByTenantIdAndCorrelationId(currentTenant, dto.idempotencyKey())) {
            throw new IllegalStateException("Idempotency breach: Correlation ID already filed under this tenant context.");
        }

        LedgerTransaction tx = LedgerTransaction.builder()
                .tenant(tenant) // Set explicitly
                .description(dto.description())
                .correlationId(dto.idempotencyKey())
                .build();

        for (LedgerLine line : dto.lines()) {
            // Find account using explicit tenant isolation query
            Account account = accountRepository.findByTenantIdAndId(currentTenant, line.accountId())
                    .orElseThrow(() -> new IllegalArgumentException("Target accounting node does not exist or belongs to another tenant."));

            LedgerEntry entry = LedgerEntry.builder()
                    .tenant(tenant) // Set explicitly
                    .account(account)
                    .debit(line.debit())
                    .credit(line.credit())
                    .build();

            tx.addEntry(entry);
        }

        // Saves cascadingly. The deferred database constraint trigger evaluates right before COMMIT executes.
        LedgerTransaction savedTx = transactionRepository.save(tx);
        //return UUID.randomUUID().toString();
        return savedTx.getId().toString();
    }

    @Transactional
    public String postTransactionAndFail(String tenantId, PostTransactionRequest request) {
        validator.validate(request.lines());
        throw new RuntimeException("Forced rollback for test");
    }
}
