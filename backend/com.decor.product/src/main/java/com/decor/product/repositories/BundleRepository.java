package com.decor.product.repositories;

import com.decor.product.entities.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleRepository extends JpaRepository<Bundle, Long> {
}