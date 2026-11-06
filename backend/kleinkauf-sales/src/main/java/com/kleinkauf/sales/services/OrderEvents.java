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
Order events for the store's live dashboard (store.activity channel, docs/features/observability.md):
- order.created: a new online order or an in-store sale (channel tells them apart).
- order.receipt-uploaded: the buyer uploaded a receipt and the order is waiting for review.
Inside a transaction they are published after the commit. Without Redis nothing is published and the order is unaffected.
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
