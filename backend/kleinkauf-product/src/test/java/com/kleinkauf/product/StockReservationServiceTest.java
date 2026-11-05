package com.kleinkauf.product;

import com.kleinkauf.common.dto.stock.ReservationDto;
import com.kleinkauf.common.dto.stock.ReservationItemDto;
import com.kleinkauf.common.dto.stock.ReservationItemType;
import com.kleinkauf.common.dto.stock.ReservationRequestDto;
import com.kleinkauf.common.dto.stock.SoldItemsDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.StockMovement;
import com.kleinkauf.product.entities.StockMovementType;
import com.kleinkauf.product.entities.StockReservation;
import com.kleinkauf.product.repositories.BundleProductRepository;
import com.kleinkauf.product.repositories.BundleRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.repositories.StockMovementRepository;
import com.kleinkauf.product.repositories.StockReservationRepository;
import com.kleinkauf.product.services.CatalogCacheService;
import com.kleinkauf.product.services.StockEvents;
import com.kleinkauf.product.services.StockReservationService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
Order stock reservations against H2 (PostgreSQL mode): validates the locking queries
and that all-or-nothing holds. Without the test transaction, each reservation really commits or rolls back.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:reservations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(StockReservationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockReservationServiceTest {

	@Autowired
	private StockReservationService service;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private BundleRepository bundleRepository;

	@Autowired
	private BundleProductRepository bundleProductRepository;

	@Autowired
	private StockMovementRepository stockMovementRepository;

	@Autowired
	private StockReservationRepository reservationRepository;

	@MockitoBean
	private CatalogCacheService catalogCache;

	@MockitoBean
	private StockEvents stockEvents;

	@Autowired
	private DataSource dataSource;

	// Plain SQL: Product's delete is a soft delete and the name would stay taken
	@AfterEach
	void clean() throws SQLException {
		try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
			for (String table : List.of("stock_movements", "stock_reservations", "bundle_products", "bundles", "product")) {
				st.execute("DELETE FROM " + table);
			}
		}
	}

	@Test
	void reserve_deductsProductsAndBundles_andReturnsNamePriceAndCost() throws ApiException {
		Product table = product("Mesa", 10, "100.00", "60.00");
		Product chair = product("Silla", 10, "20.00", "12.00");
		Bundle combo = bundle("Comedor", "150.00", table, 1, chair, 4);

		ReservationDto reservation = service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, chair.getId(), 2),
				item(ReservationItemType.BUNDLE, combo.getId(), 2)));

		// Chair: 2 standalone + 4 per bundle * 2 bundles
		assertEquals(8, stock(table));
		assertEquals(0, stock(chair));
		assertEquals("Silla", reservation.items().get(0).name());
		assertEquals(new BigDecimal("20.00"), reservation.items().get(0).unitPrice());
		assertEquals("Comedor", reservation.items().get(1).name());
		assertEquals(new BigDecimal("150.00"), reservation.items().get(1).unitPrice());
		// Cost of a bundle: 60 + 4 * 12
		assertEquals(0, new BigDecimal("108.00").compareTo(reservation.items().get(1).unitCost()));

		List<StockMovement> movements = stockMovementRepository.findAll();
		assertEquals(2, movements.size());
		assertTrue(movements.stream().allMatch(m -> m.getType() == StockMovementType.SALIDA
				&& "K7QM-4X2P".equals(m.getReference())));
	}

	@Test
	void reserve_notEnoughStock_deductsNothing() {
		Product table = product("Mesa", 5, "100.00", null);
		Product chair = product("Silla", 1, "20.00", null);

		ApiException e = assertThrows(ApiException.class, () -> service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, table.getId(), 2),
				item(ReservationItemType.PRODUCT, chair.getId(), 2))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertTrue(e.getMessage().contains("Silla"));
		assertEquals(5, stock(table));
		assertEquals(1, stock(chair));
		assertTrue(stockMovementRepository.findAll().isEmpty());
		// The reservation is rolled back too: the same number can be used again
		assertTrue(reservationRepository.findByReference("K7QM-4X2P").isEmpty());
	}

	@Test
	void reserve_productWithoutPrice_isConflict() {
		Product table = product("Mesa", 5, null, null);

		ApiException e = assertThrows(ApiException.class, () -> service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, table.getId(), 1))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(5, stock(table));
	}

	@Test
	void reserve_unknownProductOrDisabledBundle_isConflict() {
		Product table = product("Mesa", 5, "100.00", null);
		Bundle combo = bundle("Comedor", "150.00", table, 1, null, 0);
		combo.setEnabled(false);
		bundleRepository.save(combo);

		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.reserve(request("A",
				item(ReservationItemType.PRODUCT, 999L, 1)))).getStatus());
		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.reserve(request("B",
				item(ReservationItemType.BUNDLE, combo.getId(), 1)))).getStatus());
		assertEquals(5, stock(table));
	}

	@Test
	void reserve_retry_doesNotDeductTwice() throws ApiException {
		Product table = product("Mesa", 5, "100.00", null);
		ReservationRequestDto request = request("K7QM-4X2P", item(ReservationItemType.PRODUCT, table.getId(), 2));

		service.reserve(request);
		ReservationDto again = service.reserve(request);

		assertEquals(3, stock(table));
		assertEquals("Mesa", again.items().get(0).name());
		assertEquals(1, stockMovementRepository.findAll().size());
	}

	@Test
	void release_returnsTheStock_andIsIdempotent() throws ApiException {
		Product table = product("Mesa", 5, "100.00", null);
		service.reserve(request("K7QM-4X2P", item(ReservationItemType.PRODUCT, table.getId(), 2)));

		service.release("K7QM-4X2P");
		service.release("K7QM-4X2P");

		assertEquals(5, stock(table));
		List<StockMovement> entries = stockMovementRepository.findAll().stream()
				.filter(m -> m.getType() == StockMovementType.ENTRADA)
				.toList();
		assertEquals(1, entries.size());
		assertEquals(2, entries.get(0).getQuantity());
		assertEquals(StockReservation.Status.RELEASED,
				reservationRepository.findByReference("K7QM-4X2P").orElseThrow().getStatus());
	}

	@Test
	void releaseBeforeReserve_theReservationArrivingLaterIsRejected() throws ApiException {
		Product table = product("Mesa", 5, "100.00", null);

		service.release("K7QM-4X2P");
		ApiException e = assertThrows(ApiException.class, () -> service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, table.getId(), 2))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(5, stock(table));
	}

	@Test
	void bundleWithProductWithoutCost_nullCost() throws ApiException {
		Product table = product("Mesa", 5, "100.00", "60.00");
		Product chair = product("Silla", 5, "20.00", null);
		Bundle combo = bundle("Comedor", "110.00", table, 1, chair, 1);

		ReservationDto reservation = service.reserve(request("K7QM-4X2P", item(ReservationItemType.BUNDLE, combo.getId(), 1)));

		assertNull(reservation.items().get(0).unitCost());
	}

	@Test
	void inStoreSale_withoutStock_adjustsAndLeavesAtZero_andVoidingReturnsEverything() throws ApiException {
		Product table = product("Mesa", 2, null, null);

		ReservationDto reservation = service.reserve(new ReservationRequestDto("LOCA-L001",
				List.of(item(ReservationItemType.PRODUCT, table.getId(), 5)), true));

		// Without a price ('a consultar') it can be sold in the store
		assertNull(reservation.items().get(0).unitPrice());
		assertEquals(List.of("Mesa"), reservation.shortages());
		assertEquals(0, stock(table));
		List<StockMovement> movements = stockMovementRepository.findAll();
		assertTrue(movements.stream().anyMatch(m -> m.getType() == StockMovementType.AJUSTE && m.getQuantity() == 3));
		assertTrue(movements.stream().anyMatch(m -> m.getType() == StockMovementType.SALIDA && m.getQuantity() == -5));

		// The retry reports the same
		assertEquals(List.of("Mesa"), service.reserve(new ReservationRequestDto("LOCA-L001",
				List.of(item(ReservationItemType.PRODUCT, table.getId(), 5)), true)).shortages());

		// Voiding returns everything sold
		service.release("LOCA-L001");
		assertEquals(5, stock(table));
	}

	@Test
	void sold_addsAndSubtractsOnlyOnce() throws ApiException {
		Product table = product("Mesa", 10, "100.00", null);
		Bundle combo = bundle("Comedor", "150.00", table, 1, null, 0);
		service.reserve(request("K7QM-4X2P", item(ReservationItemType.PRODUCT, table.getId(), 2),
				item(ReservationItemType.BUNDLE, combo.getId(), 1)));
		SoldItemsDto sold = new SoldItemsDto(List.of(item(ReservationItemType.PRODUCT, table.getId(), 2),
				item(ReservationItemType.BUNDLE, combo.getId(), 1)));

		service.markSold("K7QM-4X2P", sold);
		service.markSold("K7QM-4X2P", sold);

		assertEquals(2, productRepository.findById(table.getId()).orElseThrow().getSoldCount());
		assertEquals(1, bundleRepository.findById(combo.getId()).orElseThrow().getSoldCount());

		service.unmarkSold("K7QM-4X2P", sold);
		service.unmarkSold("K7QM-4X2P", sold);

		assertEquals(0, productRepository.findById(table.getId()).orElseThrow().getSoldCount());
		assertEquals(0, bundleRepository.findById(combo.getId()).orElseThrow().getSoldCount());
	}

	@Test
	void sold_ofAReleasedReservation_isConflict() throws ApiException {
		Product table = product("Mesa", 10, "100.00", null);
		service.reserve(request("K7QM-4X2P", item(ReservationItemType.PRODUCT, table.getId(), 1)));
		service.release("K7QM-4X2P");

		ApiException e = assertThrows(ApiException.class, () -> service.markSold("K7QM-4X2P",
				new SoldItemsDto(List.of(item(ReservationItemType.PRODUCT, table.getId(), 1)))));
		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.markSold("NO-EXISTE",
				new SoldItemsDto(List.of()))).getStatus());
	}

	@Test
	void invalidRequest_isBadRequest() {
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
				() -> service.reserve(new ReservationRequestDto(" ", List.of(item(ReservationItemType.PRODUCT, 1L, 1)), false))).getStatus());
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
				() -> service.reserve(request("A", item(ReservationItemType.PRODUCT, 1L, 0)))).getStatus());
	}

	private Product product(String name, int stock, String price, String cost) {
		Product p = new Product();
		p.setName(name);
		p.setSlug(name.toLowerCase());
		p.setStock(stock);
		p.setPrice(price == null ? null : new BigDecimal(price));
		p.setCost(cost == null ? null : new BigDecimal(cost));
		return productRepository.save(p);
	}

	// Bundle with one or two products (second null = only one)
	private Bundle bundle(String name, String price, Product first, int firstQuantity, Product second, int secondQuantity) {
		Bundle b = new Bundle();
		b.setName(name);
		b.setEnabled(true);
		b.setPrice(new BigDecimal(price));
		b = bundleRepository.save(b);
		component(b, first, firstQuantity);
		if (second != null) component(b, second, secondQuantity);
		return b;
	}

	private void component(Bundle bundle, Product product, int quantity) {
		BundleProduct bp = new BundleProduct();
		bp.setBundle(bundle);
		bp.setProduct(product);
		bp.setQuantity(quantity);
		bundleProductRepository.save(bp);
	}

	private int stock(Product p) {
		return productRepository.findById(p.getId()).orElseThrow().getStock();
	}

	private static ReservationItemDto item(ReservationItemType type, Long id, int quantity) {
		return new ReservationItemDto(type, id, quantity);
	}

	private static ReservationRequestDto request(String reference, ReservationItemDto... items) {
		return new ReservationRequestDto(reference, List.of(items), false);
	}
}
