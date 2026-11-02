package com.gregomebije.ledger.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @Column(length = 64)
    private String id; // Matches 'tenant-a'

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "plan_tier", nullable = false, length = 32)
    private String planTier = "FREE";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public Tenant(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
