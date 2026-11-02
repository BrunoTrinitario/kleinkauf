package com.decor.product.repositories;

import com.decor.product.entities.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BundleRepository extends JpaRepository<Bundle, Long> {
    List<Bundle> findByEnabledTrueOrderByNameAsc();
}
