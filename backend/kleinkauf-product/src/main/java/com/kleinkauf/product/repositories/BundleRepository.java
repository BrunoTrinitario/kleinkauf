package com.kleinkauf.product.repositories;

import com.kleinkauf.product.entities.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BundleRepository extends JpaRepository<Bundle, Long> {
    List<Bundle> findByEnabledTrueOrderByNameAsc();
}
