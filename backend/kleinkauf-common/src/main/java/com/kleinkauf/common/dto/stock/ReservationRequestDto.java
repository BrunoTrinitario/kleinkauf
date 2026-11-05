package com.kleinkauf.common.dto.stock;

import java.util.List;

/*
Stock reservation request from sales to products. reference is the order number:
products uses it to avoid deducting twice if sales retries.
local: in-store sale. Accepts items without a price ('a consultar') and, if stock is short, products records
an AJUSTE for the missing amount (stock ends at 0) and reports it in ReservationDto.shortages.
 */
public record ReservationRequestDto(
        String reference,
        List<ReservationItemDto> items,
        boolean local
) {
}
