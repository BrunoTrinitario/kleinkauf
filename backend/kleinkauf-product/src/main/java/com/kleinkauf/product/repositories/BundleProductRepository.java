package com.kleinkauf.product.repositories;

import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BundleProductRepository extends JpaRepository<BundleProduct, Long> {
    Optional<BundleProduct> findByBundleAndProduct(
            Bundle bundle,
            Product product
    );

    // [productId, quantity] of a bundle. Ids only: the products are read later with a lock
    @Query("SELECT bp.product.id, bp.quantity FROM BundleProduct bp WHERE bp.bundle.id = :bundleId")
    List<Object[]> findComponents(@Param("bundleId") Long bundleId);
}
