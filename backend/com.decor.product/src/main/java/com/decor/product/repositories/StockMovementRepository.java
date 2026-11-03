package com.decor.product.repositories;

import com.decor.product.entities.StockMovement;
import com.decor.product.entities.StockMovementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProduct_IdOrderByCreatedAtDescIdDesc(Long productId);

    // [productId, quantity] de los movimientos de un pedido. Solo ids: el producto puede estar borrado
    @Query("SELECT m.product.id, m.quantity FROM StockMovement m WHERE m.reference = :reference AND m.type = :type")
    List<Object[]> findQuantitiesByReference(@Param("reference") String reference, @Param("type") StockMovementType type);

    // Ultimos movimientos de la tienda para el panel (el nombre lo agrega DashboardService).
    // Solo el id del producto (sin cargarlo): puede estar borrado
    @Query("SELECT new com.decor.product.repositories.StockMovementRepository$MovementRow("
            + "m.id, m.product.id, m.type, m.quantity, m.stockAfter, m.reason, m.reference, m.userId, m.createdAt) "
            + "FROM StockMovement m ORDER BY m.createdAt DESC, m.id DESC")
    List<MovementRow> findRecent(Pageable pageable);

    record MovementRow(Long id, Long productId, StockMovementType type, int quantity, int stockAfter,
                       String reason, String reference, Long userId, LocalDateTime createdAt) {
    }
}
