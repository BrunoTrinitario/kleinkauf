package com.decor.users.repositories;

import com.decor.users.entities.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySchema(String schema);
    boolean existsByUrl(String url);
}
