package com.kleinkauf.product.entities;

public enum StockMovementType {
    // Adds units (purchase, return)
    ENTRADA,
    // Subtracts units (sale, breakage, loss)
    SALIDA,
    // Sets the stock to a value (physical count)
    AJUSTE
}
