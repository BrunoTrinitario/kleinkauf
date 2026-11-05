package com.kleinkauf.product.entities;

// How a bundle's price is set. FIXED: the stored price. PERCENT and AMOUNT: computed over the sum of its
// products (BundleFactory.price), so it follows the products' price without having to update the bundle
public enum BundlePricingType {
    FIXED,
    PERCENT,
    AMOUNT
}
