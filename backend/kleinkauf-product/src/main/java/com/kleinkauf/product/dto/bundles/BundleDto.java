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
    // Final bundle price (with the discount applied if it is not FIXED); null = no price
    private BigDecimal price;
    // FIXED, PERCENT o AMOUNT
    private BundlePricingType pricingType;
    // Discount percentage or amount; null in FIXED
    private BigDecimal discount;
    // Sum of price * quantity of its products (null if any has no price)
    private BigDecimal listPrice;
    // How many bundles can be put together with the current stock
    private int available;
    private List<BundleProductDto> products;
}
