package com.kleinkauf.product.dto.dashboard;

import com.kleinkauf.product.entities.StockMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
GET /api/v1/products/dashboard: store dashboard (ADMINISTRADOR only, docs/features/admin-dashboards.md).
Always up to date: not cached.
 */
public record ProductDashboardDto(
        Products products,
        Stock stock
) {

    public record Products(
            long total,
            // Highest to lowest; those without a category go with category null
            List<CategoryCount> byCategory,
            long withoutPrice,
            long withoutImages,
            long featured
    ) {
    }

    public record CategoryCount(String category, long count) {
    }

    public record Stock(
            // Same criterion as GET /products/stock/low: minStock > 0 and stock <= minStock
            long low,
            long outOfStock,
            // Sum of stock x cost / price of those with stock; those without cost / price are counted separately
            BigDecimal inventoryAtCost,
            BigDecimal inventoryAtPrice,
            long excludedWithoutCost,
            long excludedWithoutPrice,
            List<RecentMovement> recentMovements
    ) {
    }

    // productName includes deleted products: the movement is still history
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
