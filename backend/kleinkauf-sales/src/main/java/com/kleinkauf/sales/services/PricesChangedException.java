package com.kleinkauf.sales.services;

import com.kleinkauf.sales.dto.PriceChangeDto;
import lombok.Getter;

import java.util.List;

// El precio de algun item cambio entre el carrito y el pedido (ver SalesExceptionHandler)
@Getter
public class PricesChangedException extends Exception {

    private final List<PriceChangeDto> changes;

    public PricesChangedException(List<PriceChangeDto> changes) {
        super("Prices changed");
        this.changes = changes;
    }
}
