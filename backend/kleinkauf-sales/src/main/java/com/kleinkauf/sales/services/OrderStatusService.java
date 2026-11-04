package com.kleinkauf.sales.services;

import java.util.LinkedHashMap;
import java.util.Map;
import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.constants.ErrorMessages;
import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.repositories.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/*
Cambios de estado de un pedido y sus efectos en products:
- PAGADO: suma el pedido al contador de vendidos.
- RECHAZADO: el plazo para subir otro comprobante corre de nuevo desde el rechazo.
- CANCELADO / VENCIDO: devuelve el stock; si ya estaba pago, lo resta de vendidos.
Primero se actualiza products (idempotente) y despues se guarda el pedido: si products falla, el pedido
no cambia y se puede reintentar. El pedido tiene control optimista (@Version): si otro lo cambio
mientras tanto responde 409.
Las llamadas a este servicio tienen que estar dentro de una transaccion (items e historial son lazy).
 */
@Slf4j
@Service
public class OrderStatusService {

    public static final String BUYER_CANCEL_REASON = "Cancelado por el comprador";
    public static final String EXPIRED_REASON = "Vencio el plazo para pagar";

    // Registro a mano de lo que no es un cambio de una entidad @Audited (docs/auditoria.md)
    @Autowired
    private AuditLog auditLog;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStock orderStock;

    @Autowired
    private SalesSettingsService settingsService;

    // Cambio del administrador (ver OrderStatus.canChangeTo; una venta del local entregada tambien se anula)
    public Order change(Order order, OrderStatus target, Long userId, String reason) throws ApiException {
        OrderStatus current = order.getStatus();
        if (target == null || !current.canChangeTo(target, order.getChannel())) {
            throw new ApiException(ErrorMessages.INVALID_STATUS_CHANGE.formatted(current, target), HttpStatus.CONFLICT);
        }
        String cleanReason = OrderValidation.trimToNull(reason);
        boolean needsReason = target == OrderStatus.RECHAZADO || target == OrderStatus.CANCELADO;
        if ((needsReason && cleanReason == null) || (cleanReason != null && cleanReason.length() > 500)) {
            throw new ApiException(ErrorMessages.REASON_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        switch (target) {
            case PAGADO -> orderStock.markSold(order);
            case RECHAZADO -> order.setExpiresAt(LocalDateTime.now().plusHours(settingsService.current().getExpirationHours()));
            case CANCELADO -> returnStock(order);
            default -> {
            }
        }
        order.changeStatus(target, userId, cleanReason);
        Order saved = save(order, target == OrderStatus.PAGADO);
        Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
        changes.put("status", AuditLog.change(current, target));
        if (cleanReason != null) changes.put("reason", AuditLog.change(null, cleanReason));
        auditLog.record(AuditEvent.STATUS_CHANGE, "Order", saved.getNumber(), changes);
        return saved;
    }

    // Cancelacion del comprador: solo hasta que se valida el pago
    public Order cancelByBuyer(Order order, Long userId) throws ApiException {
        if (!order.getStatus().isAwaitingPayment()) {
            throw new ApiException(ErrorMessages.CANCEL_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        orderStock.release(order.getNumber());
        order.changeStatus(OrderStatus.CANCELADO, userId, BUYER_CANCEL_REASON);
        return save(order, false);
    }

    // Lo usa ExpirationJob, un pedido por transaccion
    @Transactional(rollbackFor = Exception.class)
    public boolean expire(String number, LocalDateTime now) throws ApiException {
        Order order = orderRepository.findByNumber(number).orElse(null);
        return order != null && expire(order, now);
    }

    // false si el pedido ya no estaba para vencer (subio el comprobante, lo cancelaron...)
    public boolean expire(Order order, LocalDateTime now) throws ApiException {
        if (!order.getStatus().acceptsReceipt() || order.getExpiresAt() == null || order.getExpiresAt().isAfter(now)) {
            return false;
        }
        orderStock.release(order.getNumber());
        order.changeStatus(OrderStatus.VENCIDO, null, EXPIRED_REASON);
        save(order, false);
        return true;
    }

    private void returnStock(Order order) throws ApiException {
        orderStock.release(order.getNumber());
        if (order.getStatus().isPaid()) {
            orderStock.unmarkSold(order);
        }
    }

    // soldJustMarked: si no se puede guardar, se resta de vendidos lo que se acaba de sumar
    private Order save(Order order, boolean soldJustMarked) throws ApiException {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            if (soldJustMarked) orderStock.unmarkSoldQuietly(order.getNumber(), OrderStock.soldItems(order));
            throw new ApiException(ErrorMessages.ORDER_CHANGED, HttpStatus.CONFLICT);
        }
    }
}
