package com.kleinkauf.common.dto.stock;

import java.util.List;

// Items of an order to add to or subtract from the sold counter of each product and bundle
public record SoldItemsDto(
        List<ReservationItemDto> items
) {
}
