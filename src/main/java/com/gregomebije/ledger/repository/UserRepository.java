package com.gregomebije.ledger.repository;

import com.gregomebije.ledger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // Explicitly scope authentication lookups to avoid cross-tenant authentication leaks
    Optional<User> findByTenantIdAndEmail(String tenantId, String email);
}
