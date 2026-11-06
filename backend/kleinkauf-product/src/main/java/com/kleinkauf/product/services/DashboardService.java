package com.kleinkauf.product.services;

import com.kleinkauf.product.dto.dashboard.ProductDashboardDto;
import com.kleinkauf.product.dto.dashboard.ProductDashboardDto.CategoryCount;
import com.kleinkauf.product.dto.dashboard.ProductDashboardDto.RecentMovement;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.repositories.StockMovementRepository;
import com.kleinkauf.product.repositories.StockMovementRepository.MovementRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
Store dashboard (docs/features/admin-dashboards.md): products and stock with today's data, always up to date (no cache).
Each value is a count or sum query in the database: products are not loaded.
 */
@Service
public class DashboardService {

    static final int RECENT_MOVEMENTS = 10;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public ProductDashboardDto dashboard() {
        List<CategoryCount> byCategory = productRepository.countByCategory().stream()
                .map(row -> new CategoryCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
        ProductDashboardDto.Products products = new ProductDashboardDto.Products(
                productRepository.count(),
                byCategory,
                productRepository.countByPriceIsNull(),
                productRepository.countWithoutImages(),
                productRepository.countByFeaturedTrue());

        ProductDashboardDto.Stock stock = new ProductDashboardDto.Stock(
                productRepository.countLowStock(),
                productRepository.countByStockLessThanEqual(0),
                money(productRepository.inventoryAtCost()),
                money(productRepository.inventoryAtPrice()),
                productRepository.countByStockGreaterThanAndCostIsNull(0),
                productRepository.countByStockGreaterThanAndPriceIsNull(0),
                recentMovements());
        return new ProductDashboardDto(products, stock);
    }

    private List<RecentMovement> recentMovements() {
        List<MovementRow> rows = stockMovementRepository.findRecent(PageRequest.of(0, RECENT_MOVEMENTS));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = rows.stream().map(MovementRow::productId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (Object[] row : productRepository.findNamesIncludingDeleted(ids)) {
            names.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return rows.stream()
                .map(m -> new RecentMovement(m.id(), m.productId(), names.get(m.productId()), m.type(),
                        m.quantity(), m.stockAfter(), m.reason(), m.reference(), m.userId(), m.createdAt()))
                .toList();
    }

    // Without products to add up, 0 (not null); always with 2 decimals, like prices
    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
