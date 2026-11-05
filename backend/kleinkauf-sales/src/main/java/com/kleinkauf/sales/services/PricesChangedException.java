package com.kleinkauf.sales.services;

import com.kleinkauf.sales.dto.PriceChangeDto;
import lombok.Getter;

import java.util.List;

// The price of some item changed between the cart and the order (see SalesExceptionHandler)
@Getter
public class PricesChangedException extends Exception {

    private final List<PriceChangeDto> changes;

    public PricesChangedException(List<PriceChangeDto> changes) {
        super("Prices changed");
        this.changes = changes;
    }
}
