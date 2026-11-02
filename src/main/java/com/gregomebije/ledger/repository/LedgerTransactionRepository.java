package com.gregomebije.ledger.repository;

import com.gregomebije.ledger.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByTenantIdAndId(String tenantId, UUID id);
    Optional<LedgerTransaction> findByTenantIdAndCorrelationId(String tenantId, String correlationId);
    boolean existsByTenantIdAndCorrelationId(String tenantId, String correlationId);
}
