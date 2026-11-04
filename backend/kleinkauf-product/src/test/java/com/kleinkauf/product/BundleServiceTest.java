package com.kleinkauf.product;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.dto.bundles.NewBundleDto;
import com.kleinkauf.product.dto.bundles.NewBundleProductDto;
import com.kleinkauf.product.dto.bundles.UpdateBundlePriceDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundlePricingType;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.repositories.BundleProductRepository;
import com.kleinkauf.product.repositories.BundleRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.services.BundleService;
import com.kleinkauf.product.services.CatalogCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundleServiceTest {

	@Mock
	private BundleRepository bundleRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private BundleProductRepository bundleProductRepository;
	@Mock
	private CatalogCacheService catalogCache;

	@InjectMocks
	private BundleService bundleService;

	@Test
	void crearCombo_conNombrePrecioYProductos() throws ApiException {
		when(productRepository.findAllById(anyIterable()))
				.thenReturn(List.of(product(1L, "Mesa", "50000.00"), product(2L, "Silla", "12000.00")));
		when(bundleRepository.save(any(Bundle.class))).thenAnswer(invocation -> invocation.getArgument(0));

		BundleDto dto = bundleService.createBundle(new NewBundleDto(
				"  Comedor  ", true, new BigDecimal("90000"),
				List.of(new NewBundleProductDto(1L, 1), new NewBundleProductDto(2L, 4))));

		assertEquals("Comedor", dto.getName());
		assertEquals(new BigDecimal("90000"), dto.getPrice());
		assertEquals(new BigDecimal("98000.00"), dto.getListPrice());
		assertEquals(2, dto.getProducts().size());
		assertEquals(4, dto.getProducts().get(1).quantity());
		verify(catalogCache).invalidate();
	}

	@Test
	void crearCombo_conDescuentoPorcentual_noGuardaPrecio() throws ApiException {
		when(productRepository.findAllById(anyIterable()))
				.thenReturn(List.of(product(1L, "Mesa", "50000.00"), product(2L, "Silla", "12000.00")));
		when(bundleRepository.save(any(Bundle.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Aunque mande un precio, con descuento no se guarda: el precio sale de los productos
		BundleDto dto = bundleService.createBundle(new NewBundleDto(
				"Comedor", true, new BigDecimal("1"),
				List.of(new NewBundleProductDto(1L, 1), new NewBundleProductDto(2L, 4)),
				BundlePricingType.PERCENT, new BigDecimal("10")));

		assertEquals(BundlePricingType.PERCENT, dto.getPricingType());
		assertEquals(new BigDecimal("88200.00"), dto.getPrice());
		verify(bundleRepository).save(argThat(bundle -> bundle.getPrice() == null
				&& new BigDecimal("10").equals(bundle.getDiscount())));
	}

	@Test
	void crearCombo_descuentoInvalido_noGuarda() {
		List<NewBundleProductDto> items = List.of(new NewBundleProductDto(1L, 1));
		assertError(ErrorMessages.INVALID_BUNDLE_DISCOUNT, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null, items, BundlePricingType.PERCENT, new BigDecimal("101")));
		assertError(ErrorMessages.INVALID_BUNDLE_DISCOUNT, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null, items, BundlePricingType.AMOUNT, new BigDecimal("-1")));
		assertError(ErrorMessages.INVALID_BUNDLE_DISCOUNT, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null, items, BundlePricingType.PERCENT, null));
	}

	@Test
	void cambiarAPrecioFijo_borraElDescuento() throws ApiException {
		Bundle bundle = new Bundle();
		bundle.setId(1L);
		bundle.setPricingType(BundlePricingType.AMOUNT);
		bundle.setDiscount(new BigDecimal("5000"));
		when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));

		BundleDto dto = bundleService.updatePrice(1L, new UpdateBundlePriceDto(new BigDecimal("40000"), null, null));

		assertEquals(BundlePricingType.FIXED, bundle.getPricingType());
		assertNull(bundle.getDiscount());
		assertEquals(new BigDecimal("40000"), dto.getPrice());
		verify(catalogCache).invalidate();
	}

	@Test
	void crearCombo_sinProductos_noGuarda() {
		assertError(ErrorMessages.BUNDLE_PRODUCTS_REQUIRED, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null, List.of()));
		assertError(ErrorMessages.BUNDLE_PRODUCTS_REQUIRED, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null, null));
	}

	@Test
	void crearCombo_sinNombre_noGuarda() {
		assertError(ErrorMessages.BUNDLE_NAME_REQUIRED, HttpStatus.BAD_REQUEST,
				new NewBundleDto("   ", true, null, List.of(new NewBundleProductDto(1L, 1))));
	}

	@Test
	void crearCombo_cantidadCero_noGuarda() {
		assertError(ErrorMessages.INVALID_BUNDLE_QUANTITY, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null, List.of(new NewBundleProductDto(1L, 0))));
	}

	@Test
	void crearCombo_productoRepetido_noGuarda() {
		assertError(ErrorMessages.BUNDLE_PRODUCT_REPEATED, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, null,
						List.of(new NewBundleProductDto(1L, 1), new NewBundleProductDto(1L, 2))));
	}

	@Test
	void crearCombo_precioNegativo_noGuarda() {
		assertError(ErrorMessages.INVALID_PRICE, HttpStatus.BAD_REQUEST,
				new NewBundleDto("Comedor", true, new BigDecimal("-1"), List.of(new NewBundleProductDto(1L, 1))));
	}

	@Test
	void crearCombo_productoInexistente_404() {
		when(productRepository.findAllById(anyIterable())).thenReturn(List.of(product(1L, "Mesa", "50000.00")));

		assertError(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
				new NewBundleDto("Comedor", true, null,
						List.of(new NewBundleProductDto(1L, 1), new NewBundleProductDto(9L, 1))));
	}

	@Test
	void cambiarCantidad_noDuplicaElProducto() throws ApiException {
		Bundle bundle = new Bundle();
		bundle.setId(1L);
		Product mesa = product(1L, "Mesa", "50000.00");
		BundleProduct existing = new BundleProduct();
		existing.setBundle(bundle);
		existing.setProduct(mesa);
		existing.setQuantity(1);
		bundle.getProducts().add(existing);
		when(bundleRepository.findById(1L)).thenReturn(Optional.of(bundle));
		when(productRepository.findById(1L)).thenReturn(Optional.of(mesa));
		when(bundleProductRepository.findByBundleAndProduct(bundle, mesa)).thenReturn(Optional.of(existing));

		BundleDto dto = bundleService.addOrUpdateProductToBundle(1L, new NewBundleProductDto(1L, 3));

		assertEquals(1, dto.getProducts().size());
		assertEquals(3, dto.getProducts().get(0).quantity());
	}

	@Test
	void agregarProducto_comboOProductoInexistente_404() {
		when(bundleRepository.findById(1L)).thenReturn(Optional.empty());

		ApiException error = assertThrows(ApiException.class,
				() -> bundleService.addOrUpdateProductToBundle(1L, new NewBundleProductDto(1L, 1)));
		assertEquals(ErrorMessages.BUNDLE_NOT_FOUND, error.getMessage());

		when(bundleRepository.findById(1L)).thenReturn(Optional.of(new Bundle()));
		when(productRepository.findById(1L)).thenReturn(Optional.empty());
		error = assertThrows(ApiException.class,
				() -> bundleService.addOrUpdateProductToBundle(1L, new NewBundleProductDto(1L, 1)));
		assertEquals(ErrorMessages.PRODUCT_NOT_FOUND, error.getMessage());
	}

	@Test
	void agregarProducto_cantidadCero_noGuarda() {
		ApiException error = assertThrows(ApiException.class,
				() -> bundleService.addOrUpdateProductToBundle(1L, new NewBundleProductDto(1L, 0)));
		assertEquals(ErrorMessages.INVALID_BUNDLE_QUANTITY, error.getMessage());
		verify(bundleProductRepository, never()).save(any());
	}

	private void assertError(String message, HttpStatus status, NewBundleDto dto) {
		ApiException error = assertThrows(ApiException.class, () -> bundleService.createBundle(dto));
		assertEquals(message, error.getMessage());
		assertEquals(status, error.getStatus());
		verify(bundleRepository, never()).save(any());
	}

	private static Product product(Long id, String name, String price) {
		Product product = new Product();
		product.setId(id);
		product.setName(name);
		product.setPrice(price != null ? new BigDecimal(price) : null);
		product.setStock(10);
		return product;
	}
}
