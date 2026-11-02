package com.decor.product.repositories;

import com.decor.product.entities.ProductMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductMetaDataRepository extends JpaRepository<ProductMetaData, Long> {
    Optional<ProductMetaData> findByProduct_Id(Long id);
}