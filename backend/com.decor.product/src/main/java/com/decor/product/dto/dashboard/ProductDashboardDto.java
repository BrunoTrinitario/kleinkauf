package com.decor.product.dto.dashboard;

import com.decor.product.entities.StockMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
GET /api/v1/products/dashboard: panel de la tienda (solo ADMINISTRADOR, docs/panel-admin.md).
Siempre al dia: no se guarda en cache.
 */
public record ProductDashboardDto(
        Products products,
        Stock stock
) {

    public record Products(
            long total,
            // De mayor a menor; los sin categoria van con category null
            List<CategoryCount> byCategory,
            long withoutPrice,
            long withoutImages,
            long featured
    ) {
    }

    public record CategoryCount(String category, long count) {
    }

    public record Stock(
            // Mismo criterio que GET /products/stock/low: minStock > 0 y stock <= minStock
            long low,
            long outOfStock,
            // Suma de stock x costo / precio de los que tienen stock; los que no tienen costo / precio se cuentan aparte
            BigDecimal inventoryAtCost,
            BigDecimal inventoryAtPrice,
            long excludedWithoutCost,
            long excludedWithoutPrice,
            List<RecentMovement> recentMovements
    ) {
    }

    // productName incluye los productos borrados: el movimiento sigue siendo historia
    public record RecentMovement(
            Long id,
            Long productId,
            String productName,
            StockMovementType type,
            int quantity,
            int stockAfter,
            String reason,
            String reference,
            Long userId,
            LocalDateTime createdAt
    ) {
    }
}
