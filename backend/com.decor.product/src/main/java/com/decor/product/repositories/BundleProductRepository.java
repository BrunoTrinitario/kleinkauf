package com.decor.product.repositories;

import com.decor.product.entities.Bundle;
import com.decor.product.entities.BundleProduct;
import com.decor.product.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BundleProductRepository extends JpaRepository<BundleProduct, Long> {
    Optional<BundleProduct> findByBundleAndProduct(
            Bundle bundle,
            Product product
    );
}