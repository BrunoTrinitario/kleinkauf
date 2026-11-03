package com.kleinkauf.users.repositories;

import com.kleinkauf.users.entities.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    Optional<RolePermission> findByType(String type);
}
