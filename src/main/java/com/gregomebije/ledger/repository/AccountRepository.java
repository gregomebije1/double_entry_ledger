package com.gregomebije.ledger.repository;

import com.gregomebije.ledger.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByTenantIdAndId(String tenantId, UUID id);
    Optional<Account> findByTenantIdAndAccountNumber(String tenantId, String accountNumber);
    List<Account> findAllByTenantId(String tenantId);
}
