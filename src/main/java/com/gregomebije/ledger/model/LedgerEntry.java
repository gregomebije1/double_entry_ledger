package com.gregomebije.ledger.model;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;

import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One debit-or-credit line (minor units). Exactly one side is > 0.
 * Immutable: the DB also blocks UPDATE/DELETE via trg_immutable_entries.
 * Create entries through LedgerTransaction.debit()/credit().
 */
@Entity
@Immutable
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_entries_tenant_account_date", 
            columnList = "tenant_id, account_id, created_at DESC"),
        @Index(name = "idx_ledger_entries_tenant_transaction_id", 
            columnList = "tenant_id, transaction_id")
    }
)
@Check(name = "ck_entry_one_side",
    constraints = "(debit > 0 AND credit = 0) or (debit = 0 AND credit > 0)")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Min(0)
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private long debit = 0L;

    @Min(0)
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private long credit = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private OffsetDateTime createdAt;

    LedgerEntry(LedgerTransaction transaction, Account account, long debit, long credit) {
        this.transaction = transaction;
        this.tenant = transaction.getTenant();
        this.account = account;
        this.debit = debit;
        this.credit = credit;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    /** Mirrors ck_entry_one_side so bad data fails before hitting the DB. */
    @AssertTrue(message = "Exactly one of debit or credit must be greater than zero")
    private boolean isExactlyOneSidePopulated() {
        return (debit > 0 && credit == 0) || (debit == 0 && credit > 0);
    }
}
