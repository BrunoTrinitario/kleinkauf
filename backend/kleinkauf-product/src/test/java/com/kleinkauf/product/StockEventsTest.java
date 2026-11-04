package com.kleinkauf.product;

import com.kleinkauf.common.events.KleinKaufEvent;
import com.kleinkauf.common.events.KleinKaufEventPublisher;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.StockMovement;
import com.kleinkauf.product.entities.StockMovementType;
import com.kleinkauf.product.services.StockEvents;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Live dashboard stock events (store.activity): low stock only when crossing the minimum
class StockEventsTest {

	private final KleinKaufEventPublisher publisher = mock(KleinKaufEventPublisher.class);
	private StockEvents events;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ObjectProvider<KleinKaufEventPublisher> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(publisher);
		events = new StockEvents(provider);
		TenantContext.setCurrentTenant("tienda1");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	@SuppressWarnings("unchecked")
	void lowStock_whenCrossingTheMinimum() {
		events.lowIfCrossed(product(3, 3), 5);

		ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
		verify(publisher).publish(eq(KleinKaufEvent.STOCK_LOW),
				eq("tienda1"), data.capture());
		assertEquals("Mesa", data.getValue().get("productName"));
		assertEquals(3, data.getValue().get("stock"));
	}

	@Test
	void lowStock_isNotRepeatedWhileStillLowNorWithoutMinimum() {
		// It was already low
		events.lowIfCrossed(product(1, 3), 2);
		// No minimum configured
		events.lowIfCrossed(product(0, 0), 5);
		// Still above the minimum
		events.lowIfCrossed(product(4, 3), 9);

		verify(publisher, never()).publish(anyString(), anyString(), any());
	}

	@Test
	void withoutTenant_doesNotPublish() {
		TenantContext.clear();

		events.lowIfCrossed(product(3, 3), 5);

		verify(publisher, never()).publish(anyString(), anyString(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void movement_withTypeQuantityAndUser() {
		StockMovement movement = new StockMovement();
		movement.setType(StockMovementType.ENTRADA);
		movement.setQuantity(5);
		movement.setStockAfter(8);
		movement.setReason("Compra");
		movement.setUserId(7L);

		events.movement(product(8, 0), movement);

		ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
		verify(publisher).publish(eq(KleinKaufEvent.STOCK_MOVEMENT),
				eq("tienda1"), data.capture());
		assertEquals("ENTRADA", data.getValue().get("movementType"));
		assertEquals(5, data.getValue().get("quantity"));
		assertEquals(7L, data.getValue().get("userId"));
	}

	private static Product product(int stock, int minStock) {
		Product product = new Product();
		product.setId(12L);
		product.setName("Mesa");
		product.setStock(stock);
		product.setMinStock(minStock);
		return product;
	}
}
