package com.kleinkauf.product.services;

import com.kleinkauf.common.events.KleinKaufEvent;
import com.kleinkauf.common.events.KleinKaufEventPublisher;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.StockMovement;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Stock events for the store's live dashboard (store.activity channel, docs/features/observability.md):
- stock.movement: a movement entered manually from the dashboard. Order movements are not: order.created already arrives.
- stock.low: a product went into low stock (minStock > 0 and stock <= minStock) with any
  movement, order ones included. Only when crossing the limit, not on every movement while it stays low.
Inside a transaction they are published after the commit. Without Redis nothing is published and the stock is unaffected.
 */
@Component
public class StockEvents {

    private final ObjectProvider<KleinKaufEventPublisher> publisher;

    public StockEvents(ObjectProvider<KleinKaufEventPublisher> publisher) {
        this.publisher = publisher;
    }

    public void movement(Product product, StockMovement movement) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", product.getId());
        data.put("productName", product.getName());
        data.put("movementType", movement.getType().name());
        data.put("quantity", movement.getQuantity());
        data.put("stockAfter", movement.getStockAfter());
        data.put("reason", movement.getReason());
        data.put("userId", movement.getUserId());
        publish(KleinKaufEvent.STOCK_MOVEMENT, data);
    }

    // stockBefore: the stock before this operation's movements
    public void lowIfCrossed(Product product, int stockBefore) {
        if (!crossedLow(product.getMinStock(), stockBefore, product.getStock())) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", product.getId());
        data.put("productName", product.getName());
        data.put("stock", product.getStock());
        data.put("minStock", product.getMinStock());
        publish(KleinKaufEvent.STOCK_LOW, data);
    }

    static boolean crossedLow(int minStock, int before, int after) {
        return minStock > 0 && before > minStock && after <= minStock;
    }

    private void publish(String type, Map<String, Object> data) {
        String tenant = TenantContext.getCurrentTenant();
        KleinKaufEventPublisher events = publisher.getIfAvailable();
        if (tenant == null || events == null) {
            return;
        }
        events.publish(type, tenant, data);
    }
}
