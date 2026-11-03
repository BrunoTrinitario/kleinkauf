package com.decor.product;

import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationItemDto;
import com.decor.common.dto.stock.ReservationItemType;
import com.decor.common.dto.stock.ReservationRequestDto;
import com.decor.common.dto.stock.SoldItemsDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.entities.Bundle;
import com.decor.product.entities.BundleProduct;
import com.decor.product.entities.Product;
import com.decor.product.entities.StockMovement;
import com.decor.product.entities.StockMovementType;
import com.decor.product.entities.StockReservation;
import com.decor.product.repositories.BundleProductRepository;
import com.decor.product.repositories.BundleRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.repositories.StockMovementRepository;
import com.decor.product.repositories.StockReservationRepository;
import com.decor.product.services.CatalogCacheService;
import com.decor.product.services.StockEvents;
import com.decor.product.services.StockReservationService;
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
Reservas de stock de los pedidos contra H2 (modo PostgreSQL): valida las consultas con bloqueo
y que todo o nada se cumpla. Sin la transaccion del test, cada reserva hace commit o rollback de verdad.
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

	// SQL directo: el delete de Product es un borrado logico y el nombre quedaria ocupado
	@AfterEach
	void clean() throws SQLException {
		try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
			for (String table : List.of("stock_movements", "stock_reservations", "bundle_products", "bundles", "product")) {
				st.execute("DELETE FROM " + table);
			}
		}
	}

	@Test
	void reservar_descuentaProductosYCombos_yDevuelveNombrePrecioYCosto() throws ApiException {
		Product mesa = product("Mesa", 10, "100.00", "60.00");
		Product silla = product("Silla", 10, "20.00", "12.00");
		Bundle combo = bundle("Comedor", "150.00", mesa, 1, silla, 4);

		ReservationDto reservation = service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, silla.getId(), 2),
				item(ReservationItemType.BUNDLE, combo.getId(), 2)));

		// Silla: 2 sueltas + 4 por combo * 2 combos
		assertEquals(8, stock(mesa));
		assertEquals(0, stock(silla));
		assertEquals("Silla", reservation.items().get(0).name());
		assertEquals(new BigDecimal("20.00"), reservation.items().get(0).unitPrice());
		assertEquals("Comedor", reservation.items().get(1).name());
		assertEquals(new BigDecimal("150.00"), reservation.items().get(1).unitPrice());
		// Costo de un combo: 60 + 4 * 12
		assertEquals(0, new BigDecimal("108.00").compareTo(reservation.items().get(1).unitCost()));

		List<StockMovement> movements = stockMovementRepository.findAll();
		assertEquals(2, movements.size());
		assertTrue(movements.stream().allMatch(m -> m.getType() == StockMovementType.SALIDA
				&& "K7QM-4X2P".equals(m.getReference())));
	}

	@Test
	void reservar_sinStockSuficiente_noDescuentaNada() {
		Product mesa = product("Mesa", 5, "100.00", null);
		Product silla = product("Silla", 1, "20.00", null);

		ApiException e = assertThrows(ApiException.class, () -> service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, mesa.getId(), 2),
				item(ReservationItemType.PRODUCT, silla.getId(), 2))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertTrue(e.getMessage().contains("Silla"));
		assertEquals(5, stock(mesa));
		assertEquals(1, stock(silla));
		assertTrue(stockMovementRepository.findAll().isEmpty());
		// La reserva tambien vuelve atras: el mismo numero se puede usar de nuevo
		assertTrue(reservationRepository.findByReference("K7QM-4X2P").isEmpty());
	}

	@Test
	void reservar_productoSinPrecio_esConflicto() {
		Product mesa = product("Mesa", 5, null, null);

		ApiException e = assertThrows(ApiException.class, () -> service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, mesa.getId(), 1))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(5, stock(mesa));
	}

	@Test
	void reservar_productoInexistenteOComboDeshabilitado_esConflicto() {
		Product mesa = product("Mesa", 5, "100.00", null);
		Bundle combo = bundle("Comedor", "150.00", mesa, 1, null, 0);
		combo.setEnabled(false);
		bundleRepository.save(combo);

		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.reserve(request("A",
				item(ReservationItemType.PRODUCT, 999L, 1)))).getStatus());
		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.reserve(request("B",
				item(ReservationItemType.BUNDLE, combo.getId(), 1)))).getStatus());
		assertEquals(5, stock(mesa));
	}

	@Test
	void reservar_reintento_noDescuentaDosVeces() throws ApiException {
		Product mesa = product("Mesa", 5, "100.00", null);
		ReservationRequestDto request = request("K7QM-4X2P", item(ReservationItemType.PRODUCT, mesa.getId(), 2));

		service.reserve(request);
		ReservationDto again = service.reserve(request);

		assertEquals(3, stock(mesa));
		assertEquals("Mesa", again.items().get(0).name());
		assertEquals(1, stockMovementRepository.findAll().size());
	}

	@Test
	void devolver_devuelveElStock_yEsIdempotente() throws ApiException {
		Product mesa = product("Mesa", 5, "100.00", null);
		service.reserve(request("K7QM-4X2P", item(ReservationItemType.PRODUCT, mesa.getId(), 2)));

		service.release("K7QM-4X2P");
		service.release("K7QM-4X2P");

		assertEquals(5, stock(mesa));
		List<StockMovement> entradas = stockMovementRepository.findAll().stream()
				.filter(m -> m.getType() == StockMovementType.ENTRADA)
				.toList();
		assertEquals(1, entradas.size());
		assertEquals(2, entradas.get(0).getQuantity());
		assertEquals(StockReservation.Status.RELEASED,
				reservationRepository.findByReference("K7QM-4X2P").orElseThrow().getStatus());
	}

	@Test
	void devolverAntesDeReservar_laReservaQueLlegaDespuesSeRechaza() throws ApiException {
		Product mesa = product("Mesa", 5, "100.00", null);

		service.release("K7QM-4X2P");
		ApiException e = assertThrows(ApiException.class, () -> service.reserve(request("K7QM-4X2P",
				item(ReservationItemType.PRODUCT, mesa.getId(), 2))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(5, stock(mesa));
	}

	@Test
	void comboConProductoSinCosto_costoNull() throws ApiException {
		Product mesa = product("Mesa", 5, "100.00", "60.00");
		Product silla = product("Silla", 5, "20.00", null);
		Bundle combo = bundle("Comedor", "110.00", mesa, 1, silla, 1);

		ReservationDto reservation = service.reserve(request("K7QM-4X2P", item(ReservationItemType.BUNDLE, combo.getId(), 1)));

		assertNull(reservation.items().get(0).unitCost());
	}

	@Test
	void ventaDelLocal_sinStock_ajustaYDejaEnCero_yAnularDevuelveTodo() throws ApiException {
		Product mesa = product("Mesa", 2, null, null);

		ReservationDto reservation = service.reserve(new ReservationRequestDto("LOCA-L001",
				List.of(item(ReservationItemType.PRODUCT, mesa.getId(), 5)), true));

		// Sin precio ('a consultar') se puede vender en el local
		assertNull(reservation.items().get(0).unitPrice());
		assertEquals(List.of("Mesa"), reservation.shortages());
		assertEquals(0, stock(mesa));
		List<StockMovement> movements = stockMovementRepository.findAll();
		assertTrue(movements.stream().anyMatch(m -> m.getType() == StockMovementType.AJUSTE && m.getQuantity() == 3));
		assertTrue(movements.stream().anyMatch(m -> m.getType() == StockMovementType.SALIDA && m.getQuantity() == -5));

		// El reintento informa lo mismo
		assertEquals(List.of("Mesa"), service.reserve(new ReservationRequestDto("LOCA-L001",
				List.of(item(ReservationItemType.PRODUCT, mesa.getId(), 5)), true)).shortages());

		// Anular devuelve todo lo vendido
		service.release("LOCA-L001");
		assertEquals(5, stock(mesa));
	}

	@Test
	void vendidos_sumaYRestaUnaSolaVez() throws ApiException {
		Product mesa = product("Mesa", 10, "100.00", null);
		Bundle combo = bundle("Comedor", "150.00", mesa, 1, null, 0);
		service.reserve(request("K7QM-4X2P", item(ReservationItemType.PRODUCT, mesa.getId(), 2),
				item(ReservationItemType.BUNDLE, combo.getId(), 1)));
		SoldItemsDto sold = new SoldItemsDto(List.of(item(ReservationItemType.PRODUCT, mesa.getId(), 2),
				item(ReservationItemType.BUNDLE, combo.getId(), 1)));

		service.markSold("K7QM-4X2P", sold);
		service.markSold("K7QM-4X2P", sold);

		assertEquals(2, productRepository.findById(mesa.getId()).orElseThrow().getSoldCount());
		assertEquals(1, bundleRepository.findById(combo.getId()).orElseThrow().getSoldCount());

		service.unmarkSold("K7QM-4X2P", sold);
		service.unmarkSold("K7QM-4X2P", sold);

		assertEquals(0, productRepository.findById(mesa.getId()).orElseThrow().getSoldCount());
		assertEquals(0, bundleRepository.findById(combo.getId()).orElseThrow().getSoldCount());
	}

	@Test
	void vendidos_deUnaReservaDevuelta_esConflicto() throws ApiException {
		Product mesa = product("Mesa", 10, "100.00", null);
		service.reserve(request("K7QM-4X2P", item(ReservationItemType.PRODUCT, mesa.getId(), 1)));
		service.release("K7QM-4X2P");

		ApiException e = assertThrows(ApiException.class, () -> service.markSold("K7QM-4X2P",
				new SoldItemsDto(List.of(item(ReservationItemType.PRODUCT, mesa.getId(), 1)))));
		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.markSold("NO-EXISTE",
				new SoldItemsDto(List.of()))).getStatus());
	}

	@Test
	void pedidoInvalido_esBadRequest() {
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

	// Combo con uno o dos productos (second null = uno solo)
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
