package com.kleinkauf.common.dto.stock;

import java.math.BigDecimal;

/*
Item data at reservation time. unitCost is null if some product has no cost set.
unitPrice is null only in in-store sales of items without a price ('a consultar').
 */
public record ReservedItemDto(
        ReservationItemType type,
        Long id,
        String name,
        BigDecimal unitPrice,
        BigDecimal unitCost
) {
}
