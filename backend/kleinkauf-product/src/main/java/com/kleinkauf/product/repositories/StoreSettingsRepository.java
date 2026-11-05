package com.kleinkauf.product.repositories;

import com.kleinkauf.product.entities.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// A single row per schema, with id StoreSettings.SINGLETON_ID
@Repository
public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {
}
