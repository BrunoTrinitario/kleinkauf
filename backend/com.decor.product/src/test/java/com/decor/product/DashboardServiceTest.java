package com.decor.product;

import com.decor.product.dto.dashboard.ProductDashboardDto;
import com.decor.product.dto.dashboard.ProductDashboardDto.CategoryCount;
import com.decor.product.dto.dashboard.ProductDashboardDto.RecentMovement;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.entities.StockMovement;
import com.decor.product.entities.StockMovementType;
import com.decor.product.services.DashboardService;
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
Panel de la tienda contra una base real (H2 en modo PostgreSQL): los conteos y sumas son consultas
JPQL que, si estan mal, solo fallan al arrancar o al pedir el panel.
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
	void sinProductos_todoEnCero() {
		ProductDashboardDto dashboard = dashboardService.dashboard();

		assertEquals(0, dashboard.products().total());
		assertEquals(List.of(), dashboard.products().byCategory());
		assertEquals(new BigDecimal("0.00"), dashboard.stock().inventoryAtCost());
		assertEquals(new BigDecimal("0.00"), dashboard.stock().inventoryAtPrice());
		assertEquals(List.of(), dashboard.stock().recentMovements());
	}

	@Test
	void cuentaProductosPorCategoriaSinPrecioSinImagenYDestacados() {
		Product mesa = product("Mesa", "Mesas", 2, 0, "100", "60");
		image(mesa);
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
	void stockBajoAgotadosYValorDelInventario() {
		product("Con costo y precio", null, 3, 0, "100.50", "40");
		product("Sin costo", null, 2, 5, "200", null);
		product("Sin precio", null, 4, 0, null, "10");
		product("Agotado", null, 0, 1, "999", "999");
		product("Agotado sin alerta", null, 0, 0, null, null);
		em.flush();

		ProductDashboardDto.Stock stock = dashboardService.dashboard().stock();

		// "Sin costo" (2 <= 5) y "Agotado" (0 <= 1); "Agotado sin alerta" no tiene minimo
		assertEquals(2, stock.low());
		assertEquals(2, stock.outOfStock());
		// 3 x 40 + 4 x 10
		assertEquals(new BigDecimal("160.00"), stock.inventoryAtCost());
		// 3 x 100.50 + 2 x 200; los agotados no suman
		assertEquals(new BigDecimal("701.50"), stock.inventoryAtPrice());
		assertEquals(1, stock.excludedWithoutCost());
		assertEquals(1, stock.excludedWithoutPrice());
	}

	@Test
	void ultimosMovimientos_losDiezMasNuevosConElNombreAunqueElProductoEsteBorrado() {
		Product lampara = product("Lampara", null, 0, 0, null, null);
		for (int i = 0; i < 12; i++) {
			movement(lampara, LocalDateTime.now().minusMinutes(20 - i));
		}
		Product borrado = product("Borrado", null, 0, 0, null, null);
		StockMovement ultimo = movement(borrado, LocalDateTime.now());
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE product SET deleted_at = NOW() WHERE id = " + borrado.getId())
				.executeUpdate();
		em.clear();

		List<RecentMovement> movements = dashboardService.dashboard().stock().recentMovements();

		assertEquals(10, movements.size());
		assertEquals(ultimo.getId(), movements.get(0).id());
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
