package com.decor.product;

import com.decor.common.dto.RoleDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.common.servlet.security.CustomUserDetails;
import com.decor.product.dto.stock.LowStockDto;
import com.decor.product.dto.stock.NewStockMovementDto;
import com.decor.product.dto.stock.StockMovementDto;
import com.decor.product.entities.Product;
import com.decor.product.entities.StockMovement;
import com.decor.product.entities.StockMovementType;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.repositories.StockMovementRepository;
import com.decor.product.services.CatalogCacheService;
import com.decor.product.services.StockEvents;
import com.decor.product.services.StockService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

	private static final long PRODUCT_ID = 5L;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private StockMovementRepository stockMovementRepository;

	@Mock
	private CatalogCacheService catalogCache;

	@Mock
	private StockEvents stockEvents;

	@InjectMocks
	private StockService stockService;

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void entrada_sumaStockYRegistraElMovimiento() throws ApiException {
		Product product = productWithStock(10);
		mockSaves(product);

		StockMovementDto result = stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.ENTRADA, 5, "Compra"));

		assertEquals(15, product.getStock());
		assertEquals(5, result.quantity());
		assertEquals(15, result.stockAfter());
		assertEquals("Compra", result.reason());
		verify(productRepository).save(product);
		// Panel en vivo: el movimiento y el chequeo de stock bajo con el stock de antes
		verify(stockEvents).movement(eq(product), any());
		verify(stockEvents).lowIfCrossed(product, 10);
	}

	@Test
	void salida_restaStock() throws ApiException {
		Product product = productWithStock(10);
		mockSaves(product);

		StockMovementDto result = stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.SALIDA, 4, "Venta"));

		assertEquals(6, product.getStock());
		assertEquals(-4, result.quantity());
		assertEquals(6, result.stockAfter());
	}

	@Test
	void salidaMayorAlStock_respondeConflictoYNoGuardaNada() {
		Product product = productWithStock(3);
		when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

		ApiException ex = assertThrows(ApiException.class,
				() -> stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.SALIDA, 4, "Venta")));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
		assertEquals(3, product.getStock());
		verify(productRepository, never()).save(any());
		verifyNoInteractions(stockMovementRepository);
	}

	@Test
	void ajuste_fijaElStockContadoYGuardaLaDiferencia() throws ApiException {
		Product product = productWithStock(10);
		mockSaves(product);

		StockMovementDto result = stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.AJUSTE, 7, "Conteo mensual"));

		assertEquals(7, product.getStock());
		assertEquals(-3, result.quantity());
		assertEquals(7, result.stockAfter());
	}

	@Test
	void ajusteACero_esValido() throws ApiException {
		Product product = productWithStock(2);
		mockSaves(product);

		stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.AJUSTE, 0, "Rotura total"));

		assertEquals(0, product.getStock());
	}

	@Test
	void datosInvalidos_respondenBadRequestSinTocarElProducto() {
		List<NewStockMovementDto> invalidos = List.of(
				new NewStockMovementDto(null, 5, "x"),
				new NewStockMovementDto(StockMovementType.ENTRADA, 0, "x"),
				new NewStockMovementDto(StockMovementType.SALIDA, -2, "x"),
				new NewStockMovementDto(StockMovementType.ENTRADA, null, "x"),
				new NewStockMovementDto(StockMovementType.AJUSTE, -1, "x"),
				new NewStockMovementDto(StockMovementType.AJUSTE, 5, " ")
		);

		for (NewStockMovementDto dto : invalidos) {
			ApiException ex = assertThrows(ApiException.class, () -> stockService.registerMovement(PRODUCT_ID, dto));
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus(), dto.toString());
		}
		verifyNoInteractions(productRepository, stockMovementRepository);
	}

	@Test
	void productoInexistente_respondeNotFound() {
		when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class,
				() -> stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.ENTRADA, 1, null)));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	@Test
	void elMovimientoGuardaElUsuarioLogueado() throws ApiException {
		CustomUserDetails user = new CustomUserDetails(42L, List.of(new RoleDto("EMPLEADO", 0)));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
		Product product = productWithStock(1);
		mockSaves(product);

		StockMovementDto result = stockService.registerMovement(PRODUCT_ID, new NewStockMovementDto(StockMovementType.ENTRADA, 1, null));

		assertEquals(42L, result.userId());
	}

	@Test
	void stockInicial_registraUnaEntrada() {
		Product product = productWithStock(9);
		ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);

		stockService.registerInitialStock(product);

		verify(stockMovementRepository).save(captor.capture());
		assertEquals(StockMovementType.ENTRADA, captor.getValue().getType());
		assertEquals(9, captor.getValue().getQuantity());
		assertEquals(9, captor.getValue().getStockAfter());
	}

	@Test
	void stockInicialCero_noRegistraNada() {
		stockService.registerInitialStock(productWithStock(0));

		verifyNoInteractions(stockMovementRepository);
	}

	@Test
	void stockBajo_devuelveLosProductosDelRepositorio() {
		Product product = productWithStock(1);
		product.setName("Lampara");
		product.setMinStock(3);
		when(productRepository.findLowStock()).thenReturn(List.of(product));

		assertEquals(List.of(new LowStockDto(PRODUCT_ID, "Lampara", 1, 3)), stockService.getLowStock());
	}

	@Test
	void movimientosDeProductoInexistente_respondeNotFound() {
		when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);

		ApiException ex = assertThrows(ApiException.class, () -> stockService.getMovements(PRODUCT_ID));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	private void mockSaves(Product product) {
		when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static Product productWithStock(int stock) {
		Product product = new Product();
		product.setId(PRODUCT_ID);
		product.setStock(stock);
		return product;
	}
}
