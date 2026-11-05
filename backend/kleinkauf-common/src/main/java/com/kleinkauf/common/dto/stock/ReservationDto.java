package com.kleinkauf.common.dto.stock;

import java.util.List;

/*
Reservation response: one item per requested line, in the same order.
shortages: products that ran short of stock in an in-store sale (they were adjusted); empty if nothing was short.
 */
public record ReservationDto(
        String reference,
        List<ReservedItemDto> items,
        List<String> shortages
) {
}
