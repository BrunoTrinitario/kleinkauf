package com.decor.product.services;

import com.decor.common.events.DecorEvent;
import com.decor.common.events.DecorEventPublisher;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.product.entities.Product;
import com.decor.product.entities.StockMovement;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Eventos de stock para el panel en vivo de la tienda (canal store.activity, docs/observabilidad.md):
- stock.movement: un movimiento cargado a mano desde el panel. Los de los pedidos no: ya llega order.created.
- stock.low: un producto paso a estar en stock bajo (minStock > 0 y stock <= minStock) con un movimiento
  cualquiera, tambien los de los pedidos. Solo al cruzar el limite, no en cada movimiento mientras siga bajo.
Dentro de una transaccion se publican despues del commit. Sin Redis no se publica nada y el stock sigue igual.
 */
@Component
public class StockEvents {

    private final ObjectProvider<DecorEventPublisher> publisher;

    public StockEvents(ObjectProvider<DecorEventPublisher> publisher) {
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
        publish(DecorEvent.STOCK_MOVEMENT, data);
    }

    // stockBefore: el stock antes de los movimientos de esta operacion
    public void lowIfCrossed(Product product, int stockBefore) {
        if (!crossedLow(product.getMinStock(), stockBefore, product.getStock())) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", product.getId());
        data.put("productName", product.getName());
        data.put("stock", product.getStock());
        data.put("minStock", product.getMinStock());
        publish(DecorEvent.STOCK_LOW, data);
    }

    static boolean crossedLow(int minStock, int before, int after) {
        return minStock > 0 && before > minStock && after <= minStock;
    }

    private void publish(String type, Map<String, Object> data) {
        String tenant = TenantContext.getCurrentTenant();
        DecorEventPublisher events = publisher.getIfAvailable();
        if (tenant == null || events == null) {
            return;
        }
        events.publish(type, tenant, data);
    }
}
