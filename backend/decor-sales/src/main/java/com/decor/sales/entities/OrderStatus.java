package com.decor.sales.entities;

import java.util.EnumSet;
import java.util.Set;

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
    VENCIDO;

    private static final Set<OrderStatus> FINAL = EnumSet.of(ENTREGADO, CANCELADO, VENCIDO);
    private static final Set<OrderStatus> PAID = EnumSet.of(PAGADO, EN_PREPARACION, LISTO, ENTREGADO);
    private static final Set<OrderStatus> AWAITING_PAYMENT = EnumSet.of(PENDIENTE_PAGO, PAGO_EN_REVISION, RECHAZADO);

    public boolean isFinal() {
        return FINAL.contains(this);
    }

    // Cuenta como venta (stock descontado y contador de vendidos)
    public boolean isPaid() {
        return PAID.contains(this);
    }

    // Todavia no se valido el pago: el comprador lo puede cancelar
    public boolean isAwaitingPayment() {
        return AWAITING_PAYMENT.contains(this);
    }

    // Se puede subir un comprobante (y vence si pasa el plazo)
    public boolean acceptsReceipt() {
        return this == PENDIENTE_PAGO || this == RECHAZADO;
    }

    /*
    Cambios que puede hacer el administrador:
    - Validar (PAGADO) desde cualquier estado sin pagar: tambien sin comprobante, si pago por otro medio.
    - Rechazar solo un comprobante en revision.
    - Avanzar la entrega (PAGADO -> EN_PREPARACION -> LISTO -> ENTREGADO), salteando pasos si hace falta.
    - Cancelar cualquier pedido que no este terminado.
     */
    public boolean canChangeTo(OrderStatus target) {
        if (isFinal() || target == this) return false;
        return switch (target) {
            case PAGADO -> isAwaitingPayment();
            case RECHAZADO -> this == PAGO_EN_REVISION;
            case EN_PREPARACION, LISTO, ENTREGADO -> isPaid() && target.ordinal() > ordinal();
            case CANCELADO -> true;
            default -> false;
        };
    }
}
