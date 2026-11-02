package com.decor.product.repositories;

import com.decor.product.entities.StockMovement;
import com.decor.product.entities.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProduct_IdOrderByCreatedAtDescIdDesc(Long productId);

    // [productId, quantity] de los movimientos de un pedido. Solo ids: el producto puede estar borrado
    @Query("SELECT m.product.id, m.quantity FROM StockMovement m WHERE m.reference = :reference AND m.type = :type")
    List<Object[]> findQuantitiesByReference(@Param("reference") String reference, @Param("type") StockMovementType type);
}
