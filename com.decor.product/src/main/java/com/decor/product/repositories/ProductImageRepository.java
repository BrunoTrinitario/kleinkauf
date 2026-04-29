package com.decor.product.repositories;

import com.decor.product.entities.ProductImage;
import com.decor.product.entities.ProductMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Collection<ProductImage> findByProduct_Id(Long id);
    List<ProductImage> findByProduct_IdOrderByImageOrderAsc (Long id);
}