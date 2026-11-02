package com.gregomebije.ledger.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.gregomebije.ledger.dto.Models.AccountType;

@Entity
@Table(
    name = "accounts",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_accounts_tenant_number", 
        columnNames = {"tenant_id", "account_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "account_number", nullable = false, length = 64, updatable = false)
    private String accountNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING) // Explicitly maps Java text names to Postgres Enum items
    @Column(name = "type", nullable = false,  updatable = false, columnDefinition = "account_type")
    private AccountType type; 

    @Column(length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public Account(Tenant tenant, String accountNumber, String name,
                   AccountType type, String currency) {
        this.tenant = tenant;
        this.accountNumber = accountNumber;
        this.name = name;
        this.type = type;
        this.currency = currency;
    }
}
