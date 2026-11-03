package com.kleinkauf.sales.services;

import com.kleinkauf.common.events.KleinKaufEvent;
import com.kleinkauf.common.events.KleinKaufEventPublisher;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.sales.entities.Order;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Eventos de pedidos para el panel en vivo de la tienda (canal store.activity, docs/observabilidad.md):
- order.created: un pedido online nuevo o una venta del local (channel lo distingue).
- order.receipt-uploaded: el comprador subio un comprobante y el pedido quedo para revisar.
Dentro de una transaccion se publican despues del commit. Sin Redis no se publica nada y el pedido sigue igual.
 */
@Component
public class OrderEvents {

    private final ObjectProvider<KleinKaufEventPublisher> publisher;

    public OrderEvents(ObjectProvider<KleinKaufEventPublisher> publisher) {
        this.publisher = publisher;
    }

    public void created(Order order) {
        publish(KleinKaufEvent.ORDER_CREATED, order);
    }

    public void receiptUploaded(Order order) {
        publish(KleinKaufEvent.ORDER_RECEIPT_UPLOADED, order);
    }

    private void publish(String type, Order order) {
        String tenant = TenantContext.getCurrentTenant();
        KleinKaufEventPublisher events = publisher.getIfAvailable();
        if (tenant == null || events == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("number", order.getNumber());
        data.put("channel", order.getChannel() == null ? null : order.getChannel().name());
        data.put("status", order.getStatus() == null ? null : order.getStatus().name());
        data.put("total", order.getTotal());
        data.put("buyerName", order.getBuyerName());
        events.publish(type, tenant, data);
    }
}
