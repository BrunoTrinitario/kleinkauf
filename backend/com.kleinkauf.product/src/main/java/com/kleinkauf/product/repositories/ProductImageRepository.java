package com.kleinkauf.product.repositories;

import com.kleinkauf.product.entities.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Collection<ProductImage> findByProduct_Id(Long id);
    List<ProductImage> findByProduct_IdOrderByImageOrderAsc (Long id);

    // Imagenes de varios productos en una sola consulta: cada fila es {productId, path}, en orden.
    // Proyeccion para no cargar el producto de cada imagen
    @Query("SELECT i.product.id, i.path FROM ProductImage i WHERE i.product.id IN :ids ORDER BY i.imageOrder ASC, i.id ASC")
    List<Object[]> findPathsByProductIds(@Param("ids") Collection<Long> ids);
}
