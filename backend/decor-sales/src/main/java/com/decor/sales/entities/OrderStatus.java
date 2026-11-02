package com.decor.sales.entities;

/*
Estados del pedido (ver docs/ventas.md). Cuenta como venta desde PAGADO.
El stock se reserva al crear el pedido y se devuelve en CANCELADO y VENCIDO.
 */
public enum OrderStatus {
    // Creado, esperando el comprobante
    PENDIENTE_PAGO,
    // Subio el comprobante, lo revisa el administrador
    PAGO_EN_REVISION,
    // El comprobante no es valido; puede subir otro
    RECHAZADO,
    // El administrador valido el pago
    PAGADO,
    EN_PREPARACION,
    // Listo para retirar o despachar
    LISTO,
    ENTREGADO,
    CANCELADO,
    // Paso el plazo sin comprobante
    VENCIDO
}
