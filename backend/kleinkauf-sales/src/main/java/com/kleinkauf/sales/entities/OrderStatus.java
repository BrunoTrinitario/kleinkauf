package com.kleinkauf.sales.entities;

import java.util.EnumSet;
import java.util.Set;

/*
Order statuses (see docs/features/sales.md). It counts as a sale from PAGADO on.
Stock is reserved when the order is created and returned on CANCELADO and VENCIDO.
 */
public enum OrderStatus {
    // Created, awaiting the receipt
    PENDIENTE_PAGO,
    // Receipt uploaded, the administrator reviews it
    PAGO_EN_REVISION,
    // The receipt is not valid; another one can be uploaded
    RECHAZADO,
    // The administrator validated the payment
    PAGADO,
    EN_PREPARACION,
    // Ready for pickup or dispatch
    LISTO,
    ENTREGADO,
    CANCELADO,
    // The deadline passed without a receipt
    VENCIDO;

    private static final Set<OrderStatus> FINAL = EnumSet.of(ENTREGADO, CANCELADO, VENCIDO);
    private static final Set<OrderStatus> PAID = EnumSet.of(PAGADO, EN_PREPARACION, LISTO, ENTREGADO);
    private static final Set<OrderStatus> AWAITING_PAYMENT = EnumSet.of(PENDIENTE_PAGO, PAGO_EN_REVISION, RECHAZADO);

    public boolean isFinal() {
        return FINAL.contains(this);
    }

    // Counts as a sale (stock deducted and sold counter)
    public boolean isPaid() {
        return PAID.contains(this);
    }

    // The payment has not been validated yet: the buyer can cancel it
    public boolean isAwaitingPayment() {
        return AWAITING_PAYMENT.contains(this);
    }

    // A receipt can be uploaded (and it expires if the deadline passes)
    public boolean acceptsReceipt() {
        return this == PENDIENTE_PAGO || this == RECHAZADO;
    }

    /*
    Changes the administrator can make:
    - Validate (PAGADO) from any unpaid status: also without a receipt, if they paid by other means.
    - Reject only a receipt under review.
    - Advance the delivery (PAGADO -> EN_PREPARACION -> LISTO -> ENTREGADO), skipping steps if needed.
    - Cancel any order that is not finished.
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

    /*
    The same, depending on the channel: a delivered in-store sale can be voided (entered by mistake or returned).
    Delivered online orders stay final.
     */
    public boolean canChangeTo(OrderStatus target, SalesChannel channel) {
        if (this == ENTREGADO && target == CANCELADO) return channel == SalesChannel.LOCAL;
        return canChangeTo(target);
    }
}
