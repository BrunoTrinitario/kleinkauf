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

    // [productId, quantity] de un combo. Solo ids: los productos se leen despues con bloqueo
    @Query("SELECT bp.product.id, bp.quantity FROM BundleProduct bp WHERE bp.bundle.id = :bundleId")
    List<Object[]> findComponents(@Param("bundleId") Long bundleId);
}
