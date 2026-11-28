package com.gregomebije.ledger.model ;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable transaction header. Entries are added via debit()/credit() and
 * persisted with the header (cascade PERSIST), so the deferred balance trigger
 * sees a complete, balanced set at commit. Save inside ONE @Transactional.
 */
@Entity
@Immutable
@Table(
    name = "ledger_transactions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_transactions_tenant_idempotency", 
        columnNames = {"tenant_id", "idempotency_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder


public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 512,  updatable = false)
    private String memo;

    @Column(name = "posted_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime postedAt = OffsetDateTime.now();

    /** Idempotency guard, unique per tenant. */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Explicit One-To-Many relationship pointing down to individual transaction splits (lines)
    @Builder.Default
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerEntry> entries = new ArrayList<>();

    public LedgerTransaction(Tenant tenant, String idempotencyKey, String memo) {
        this.tenant = tenant;
        this.idempotencyKey = idempotencyKey;
        this.memo = memo;
    }
    /*
    public LedgerTransaction debit(Account account, long amountMinorUnits) {
        entries.add(new LedgerEntry(this, account, amountMinorUnits, 0L));
        return this;
    }

    public LedgerTransaction credit(Account account, long amountMinorUnits) {
        entries.add(new LedgerEntry(this, account, 0L, amountMinorUnits));
        return this;
    }
    */
    
    public void addEntry(LedgerEntry entry) {
        entries.add(entry);
        entry.setTransaction(this);
    }
    
    /*
    var tx = new LedgerTransaction(tenant, "pay-123", "Customer payment")
        .debit(cash, 10_000)
        .credit(revenue, 10_000);
    transactionRepository.save(tx);
    */
}
