package com.kleinkauf.product;

import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.StockMovement;
import com.kleinkauf.product.entities.StockMovementType;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.repositories.StockMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
Valida contra una base real (H2 en modo PostgreSQL) el mapeo de las columnas nuevas
y las consultas JPQL, que si estan mal solo fallan al arrancar el servicio.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:products;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class StockRepositoryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockMovementRepository stockMovementRepository;

	@Test
	void guardaPrecioCostoYStockMinimo() {
		Product product = persist("Mesa", 4, 2);
		product.setPrice(new BigDecimal("15000.50"));
		product.setCost(new BigDecimal("9000.25"));
		em.flush();
		em.clear();

		Product loaded = productRepository.findById(product.getId()).orElseThrow();

		assertEquals(new BigDecimal("15000.50"), loaded.getPrice());
		assertEquals(new BigDecimal("9000.25"), loaded.getCost());
		assertEquals(2, loaded.getMinStock());
	}

	@Test
	void stockBajo_soloLosQueTienenMinimoYEstanEnOAbajo() {
		persist("Sin alerta", 0, 0);
		persist("Bien", 10, 3);
		persist("Justo", 3, 3);
		persist("Agotado", 0, 1);

		List<String> names = productRepository.findLowStock().stream().map(Product::getName).toList();

		// ordenados por stock ascendente
		assertEquals(List.of("Agotado", "Justo"), names);
	}

	@Test
	void destacados_porOrdenYLosSinOrdenAlFinalPorNombre() {
		featured(persist("Zeta sin orden", 1, 0), null);
		featured(persist("Segundo", 1, 0), 2);
		persist("No destacado", 1, 0);
		featured(persist("Alfa sin orden", 1, 0), null);
		featured(persist("Primero", 1, 0), 1);
		em.flush();

		List<String> names = productRepository.findFeatured().stream().map(Product::getName).toList();

		assertEquals(List.of("Primero", "Segundo", "Alfa sin orden", "Zeta sin orden"), names);
	}

	@Test
	void findByIdForUpdate_encuentraElProducto() {
		Product product = persist("Silla", 5, 0);

		assertTrue(productRepository.findByIdForUpdate(product.getId()).isPresent());
	}

	@Test
	void movimientos_delMasNuevoAlMasViejo() {
		Product product = persist("Lampara", 0, 0);
		StockMovement viejo = movement(product, LocalDateTime.now().minusDays(1));
		StockMovement nuevo = movement(product, LocalDateTime.now());

		List<StockMovement> movements = stockMovementRepository.findByProduct_IdOrderByCreatedAtDescIdDesc(product.getId());

		assertEquals(List.of(nuevo.getId(), viejo.getId()), movements.stream().map(StockMovement::getId).toList());
	}

	private Product persist(String name, int stock, int minStock) {
		Product product = new Product();
		product.setName(name);
		product.setStock(stock);
		product.setMinStock(minStock);
		return em.persist(product);
	}

	private static void featured(Product product, Integer order) {
		product.setFeatured(true);
		product.setFeaturedOrder(order);
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
