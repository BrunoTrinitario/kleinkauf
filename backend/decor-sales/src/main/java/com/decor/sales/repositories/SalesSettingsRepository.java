package com.decor.sales.repositories;

import com.decor.sales.entities.SalesSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesSettingsRepository extends JpaRepository<SalesSettings, Long> {
    Optional<SalesSettings> findFirstByOrderByIdAsc();
}
