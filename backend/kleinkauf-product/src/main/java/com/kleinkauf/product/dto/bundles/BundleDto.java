package com.kleinkauf.product.dto.bundles;

import com.kleinkauf.product.entities.BundlePricingType;
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
    // Precio final del combo (con el descuento aplicado si no es FIXED); null = sin precio
    private BigDecimal price;
    // FIXED, PERCENT o AMOUNT
    private BundlePricingType pricingType;
    // Porcentaje o monto del descuento; null en FIXED
    private BigDecimal discount;
    // Suma de precio * cantidad de sus productos (null si alguno no tiene precio)
    private BigDecimal listPrice;
    // Cuantos combos se pueden armar con el stock actual
    private int available;
    private List<BundleProductDto> products;
}
