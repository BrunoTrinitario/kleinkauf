package com.kleinkauf.users.repositories;

import com.kleinkauf.users.entities.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/*
Deleted tenants (deleted_at) do not exist for the app: lookups exclude them.
existsBySchema includes them, because their schema and data are still in the database and the
identifier cannot be used again.
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySchemaAndDeletedAtIsNull(String schema);
    Optional<Tenant> findByUrlAndDeletedAtIsNull(String url);
    Optional<Tenant> findByIdAndDeletedAtIsNull(Long id);
    List<Tenant> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    boolean existsByUrlAndDeletedAtIsNull(String url);
    boolean existsBySchema(String schema);
}
