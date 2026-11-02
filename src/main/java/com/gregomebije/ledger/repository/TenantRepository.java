package com.gregomebije.ledger.repository;

import com.gregomebije.ledger.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
    // Primary key lookup handles simple string checks safely

    <Optional>Tenant findById(long id);
	
}
