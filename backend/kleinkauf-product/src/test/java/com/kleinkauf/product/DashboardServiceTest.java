package com.kleinkauf.product;

import com.kleinkauf.product.dto.dashboard.ProductDashboardDto;
import com.kleinkauf.product.dto.dashboard.ProductDashboardDto.CategoryCount;
import com.kleinkauf.product.dto.dashboard.ProductDashboardDto.RecentMovement;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductImage;
import com.kleinkauf.product.entities.StockMovement;
import com.kleinkauf.product.entities.StockMovementType;
import com.kleinkauf.product.services.DashboardService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
Store dashboard against a real database (H2 in PostgreSQL mode): the counts and sums are JPQL
queries that, if wrong, only fail at startup or when the dashboard is requested.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:dashboard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(DashboardService.class)
class DashboardServiceTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private DashboardService dashboardService;

	@Test
	void withoutProducts_allZero() {
		ProductDashboardDto dashboard = dashboardService.dashboard();

		assertEquals(0, dashboard.products().total());
		assertEquals(List.of(), dashboard.products().byCategory());
		assertEquals(new BigDecimal("0.00"), dashboard.stock().inventoryAtCost());
		assertEquals(new BigDecimal("0.00"), dashboard.stock().inventoryAtPrice());
		assertEquals(List.of(), dashboard.stock().recentMovements());
	}

	@Test
	void countsProductsByCategoryWithoutPriceWithoutImageAndFeatured() {
		Product table = product("Mesa", "Mesas", 2, 0, "100", "60");
		image(table);
		product("Mesa ratona", "Mesas", 1, 0, null, null).setFeatured(true);
		product("Silla", "Sillas", 1, 0, "50", null);
		product("Suelto", null, 0, 0, "10", "5");
		em.flush();

		ProductDashboardDto.Products products = dashboardService.dashboard().products();

		assertEquals(4, products.total());
		assertEquals(List.of(new CategoryCount("Mesas", 2), new CategoryCount("Sillas", 1), new CategoryCount(null, 1)),
				products.byCategory());
		assertEquals(1, products.withoutPrice());
		assertEquals(3, products.withoutImages());
		assertEquals(1, products.featured());
	}

	@Test
	void lowStockOutOfStockAndInventoryValue() {
		product("Con costo y precio", null, 3, 0, "100.50", "40");
		product("Sin costo", null, 2, 5, "200", null);
		product("Sin precio", null, 4, 0, null, "10");
		product("Agotado", null, 0, 1, "999", "999");
		product("Agotado sin alerta", null, 0, 0, null, null);
		em.flush();

		ProductDashboardDto.Stock stock = dashboardService.dashboard().stock();

		// "Sin costo" (2 <= 5) and "Agotado" (0 <= 1); "Agotado sin alerta" has no minimum
		assertEquals(2, stock.low());
		assertEquals(2, stock.outOfStock());
		// 3 x 40 + 4 x 10
		assertEquals(new BigDecimal("160.00"), stock.inventoryAtCost());
		// 3 x 100.50 + 2 x 200; out-of-stock ones do not add up
		assertEquals(new BigDecimal("701.50"), stock.inventoryAtPrice());
		assertEquals(1, stock.excludedWithoutCost());
		assertEquals(1, stock.excludedWithoutPrice());
	}

	@Test
	void latestMovements_theTenNewestWithTheNameEvenIfTheProductIsDeleted() {
		Product lamp = product("Lampara", null, 0, 0, null, null);
		for (int i = 0; i < 12; i++) {
			movement(lamp, LocalDateTime.now().minusMinutes(20 - i));
		}
		Product deletedProduct = product("Borrado", null, 0, 0, null, null);
		StockMovement latest = movement(deletedProduct, LocalDateTime.now());
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE product SET deleted_at = NOW() WHERE id = " + deletedProduct.getId())
				.executeUpdate();
		em.clear();

		List<RecentMovement> movements = dashboardService.dashboard().stock().recentMovements();

		assertEquals(10, movements.size());
		assertEquals(latest.getId(), movements.get(0).id());
		assertEquals("Borrado", movements.get(0).productName());
		assertEquals("Lampara", movements.get(1).productName());
		assertEquals(StockMovementType.ENTRADA, movements.get(1).type());
	}

	private Product product(String name, String category, int stock, int minStock, String price, String cost) {
		Product product = new Product();
		product.setName(name);
		product.setCategory(category);
		product.setStock(stock);
		product.setMinStock(minStock);
		product.setPrice(price == null ? null : new BigDecimal(price));
		product.setCost(cost == null ? null : new BigDecimal(cost));
		return em.persist(product);
	}

	private void image(Product product) {
		ProductImage image = new ProductImage();
		image.setProduct(product);
		image.setPath("a.jpg");
		product.getImages().add(em.persist(image));
	}

	private StockMovement movement(Product product, LocalDateTime createdAt) {
		StockMovement movement = new StockMovement();
		movement.setProduct(product);
		movement.setType(StockMovementType.ENTRADA);
		movement.setQuantity(1);
		movement.setStockAfter(1);
		movement.setCreatedAt(createdAt);
		return em.persist(movement);
	}
}
