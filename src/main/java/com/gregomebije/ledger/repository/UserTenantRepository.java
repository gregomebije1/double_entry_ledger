package com.gregomebije.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

import com.gregomebije.ledger.model.UserTenant;
import com.gregomebije.ledger.model.User;

@Repository
public interface UserTenantRepository extends JpaRepository<UserTenant, UUID> {
    
    // Explicitly scope authentication lookups to avoid cross-tenant authentication leaks
    @Query(value = """
        SELECT ut.* FROM users_tenants ut
        INNER JOIN users u ON ut.user_id = u.id
        WHERE ut.tenant_id = :tenantId AND u.email = :email
        """, nativeQuery = true)
    Optional<UserTenant> findByTenantIdAndEmail(
        @Param("tenantId") String tenantId, 
        @Param("email") String email);
}