package com.kleinkauf.product.entities;

// Como sale el precio de un combo. FIXED: el precio guardado. PERCENT y AMOUNT: se calcula sobre la suma de sus
// productos (BundleFactory.price), asi sigue al precio de los productos sin tener que actualizar el combo
public enum BundlePricingType {
    FIXED,
    PERCENT,
    AMOUNT
}
