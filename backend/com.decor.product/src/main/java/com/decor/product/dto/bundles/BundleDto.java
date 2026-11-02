package com.decor.product.dto.bundles;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class BundleDto{
    private Long id;
    private String name;
    private boolean enabled;
    // Precio del combo
    private BigDecimal price;
    // Suma de precio * cantidad de sus productos (null si alguno no tiene precio)
    private BigDecimal listPrice;
    // Cuantos combos se pueden armar con el stock actual
    private int available;
    private List<BundleProductDto> products;
}
