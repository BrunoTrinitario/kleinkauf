package com.kleinkauf.sales.repositories;

import com.kleinkauf.sales.entities.SalesSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesSettingsRepository extends JpaRepository<SalesSettings, Long> {
    Optional<SalesSettings> findFirstByOrderByIdAsc();
}
