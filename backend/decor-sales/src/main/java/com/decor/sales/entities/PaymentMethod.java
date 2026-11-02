package com.decor.sales.entities;

// Los pedidos online son siempre TRANSFERENCIA
public enum PaymentMethod {
    EFECTIVO,
    TRANSFERENCIA,
    DEBITO,
    CREDITO,
    OTRO
}
