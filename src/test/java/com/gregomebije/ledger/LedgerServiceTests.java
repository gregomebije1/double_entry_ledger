package com.gregomebije.ledger;

import com.gregomebije.ledger.service.*;

import com.gregomebije.ledger.dto.Models.*;
import com.gregomebije.ledger.model.*;
import com.gregomebije.ledger.model.Account;
import com.gregomebije.ledger.model.LedgerTransaction;
import com.gregomebije.ledger.model.Tenant;
import com.gregomebije.ledger.exception.UnbalancedLedgerException;
import com.gregomebije.ledger.repository.AccountRepository;
import com.gregomebije.ledger.repository.LedgerTransactionRepository;
import com.gregomebije.ledger.repository.TenantRepository;
import com.gregomebije.ledger.utility.LedgerValidator;
import com.gregomebije.ledger.utility.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final String TENANT = "tenant-xyz";

    @Mock LedgerValidator validator;
    @Mock AccountRepository accountRepository;
    @Mock LedgerTransactionRepository transactionRepository;
    @Mock TenantRepository tenantRepository;
    @InjectMocks LedgerService ledgerService;

    private MockedStatic<TenantContext> tenantContext;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantContext = Mockito.mockStatic(TenantContext.class);
        tenantContext.when(TenantContext::getTenantId).thenReturn(TENANT);
        tenant = Tenant.builder().id(TENANT).build();
    }

    @AfterEach
    void tearDown() { tenantContext.close(); }

    @Test @DisplayName("createAccount -> saves and returns id")
    void createAccount_success() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(accountRepository.save(any(Account.class))).thenReturn(Account.builder().id(id).build());

        String result = ledgerService.createAccount(new CreateAccountRequest("1001", "Cash", "asset", "USD"));

        assertEquals(id.toString(), result);
        verify(accountRepository).save(any(Account.class));
    }

    @Test @DisplayName("createAccount -> unknown type -> IllegalArgumentException, nothing saved")
    void createAccount_badType() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.createAccount(new CreateAccountRequest("1", "X", "NOPE", "USD")));
        verify(accountRepository, never()).save(any());
    }

    @Test @DisplayName("postTransaction -> validator rejects -> nothing persisted (invariant 1)")
    void postTransaction_validatorRejects() {
        var line = new LedgerLine(UUID.randomUUID(), LedgerLineType.DEBIT, 100, 0);
        var dto = new CreateTransactionRequest("d", "k", List.of(line));
        doThrow(new UnbalancedLedgerException("unbalanced")).when(validator).validate(dto.lines());

        assertThrows(UnbalancedLedgerException.class, () -> ledgerService.postTransaction(dto));
        verify(transactionRepository, never()).save(any());
    }

    @Test @DisplayName("postTransaction -> duplicate idempotency key -> IllegalStateException (invariant 7)")
    void postTransaction_idempotencyBreach() {
        var dto = new CreateTransactionRequest("d", "key-123", List.of());
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(transactionRepository.existsByTenantIdAndCorrelationId(TENANT, "key-123")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> ledgerService.postTransaction(dto));
        verify(transactionRepository, never()).save(any());
    }

    @Test @DisplayName("postTransaction -> account of another tenant -> IllegalArgumentException (invariant 6)")
    void postTransaction_foreignAccount() {
        UUID foreign = UUID.randomUUID();
        var dto = new CreateTransactionRequest("d", "k", List.of(new LedgerLine(foreign, LedgerLineType.DEBIT, 100, 0)));
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(transactionRepository.existsByTenantIdAndCorrelationId(TENANT, "k")).thenReturn(false);
        when(accountRepository.findByTenantIdAndId(TENANT, foreign)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> ledgerService.postTransaction(dto));
        verify(transactionRepository, never()).save(any());
    }

    @Test @DisplayName("postTransaction -> valid -> saves once and returns tx id")
    void postTransaction_success() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), txId = UUID.randomUUID();
        var dto = new CreateTransactionRequest("Fund", "k",
                List.of(new LedgerLine(a, LedgerLineType.DEBIT, 100, 0), new LedgerLine(b, LedgerLineType.CREDIT, 0, 100)));
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(transactionRepository.existsByTenantIdAndCorrelationId(TENANT, "k")).thenReturn(false);
        when(accountRepository.findByTenantIdAndId(TENANT, a)).thenReturn(Optional.of(Account.builder().id(a).build()));
        when(accountRepository.findByTenantIdAndId(TENANT, b)).thenReturn(Optional.of(Account.builder().id(b).build()));
        when(transactionRepository.save(any(LedgerTransaction.class)))
                .thenReturn(LedgerTransaction.builder().id(txId).build());

        assertEquals(txId.toString(), ledgerService.postTransaction(dto));
        verify(validator).validate(dto.lines());
        verify(transactionRepository, times(1)).save(any(LedgerTransaction.class));
    }

    @Test @DisplayName("postTransactionAndFail -> always throws")
    void postTransactionAndFail() {
        var dto = new CreateTransactionRequest("x", "k", List.of());
        assertThrows(RuntimeException.class, () -> ledgerService.postTransactionAndFail(TENANT, dto));
        verify(validator).validate(dto.lines());
    }
}