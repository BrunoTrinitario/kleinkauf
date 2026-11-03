package com.decor.product;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.dto.catalog.Availability;
import com.decor.product.dto.catalog.CatalogPageDto;
import com.decor.product.dto.catalog.CatalogProductDetailDto;
import com.decor.product.dto.catalog.CatalogProductDto;
import com.decor.product.dto.catalog.CatalogSort;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.repositories.CatalogRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.services.CatalogCacheService;
import com.decor.product.services.CatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogServiceTest {

	@Mock
	private CatalogRepository catalogRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CatalogCacheService cache;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private CatalogService catalogService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		// El cache no tiene nada: ejecuta el loader
		when(cache.get(anyString(), any(), any())).thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(2)).get());
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
	}

	@Test
	void paginaConTotalesYDisponibilidadSinStockExacto() throws ApiException {
		Product mesa = product("Mesa", "mesa", "150000", 10, 2);
		Product silla = product("Silla", "silla", null, 1, 2);
		when(catalogRepository.count(any())).thenReturn(30L);
		when(catalogRepository.search(any(), eq(CatalogSort.PRICE_ASC), eq(1), eq(24))).thenReturn(List.of(mesa, silla));

		CatalogPageDto page = catalogService.search(" mesa ", null, "price_asc", 1, null);

		assertEquals(30, page.totalItems());
		assertEquals(2, page.totalPages());
		assertEquals(24, page.size());
		assertEquals(Availability.DISPONIBLE, page.items().get(0).availability());
		assertEquals(Availability.POCAS_UNIDADES, page.items().get(1).availability());
		// Sin precio = 'consultar'
		assertNull(page.items().get(1).price());
	}

	@Test
	void parametrosInvalidos_respondenBadRequest() {
		for (Executable call : List.<Executable>of(
				() -> catalogService.search(null, null, "barato", 0, 24),
				() -> catalogService.search(null, null, null, -1, 24),
				() -> catalogService.search(null, null, null, 0, 0),
				() -> catalogService.search(null, null, null, 0, 101))) {
			ApiException ex = assertThrows(ApiException.class, call);
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		}
	}

	@Test
	void detallePorSlug_conImagenesEnOrden() throws ApiException {
		Product mesa = product("Mesa", "mesa", "150000", 0, 0);
		mesa.getImages().add(image(mesa, "b.jpg", 1));
		mesa.getImages().add(image(mesa, "a.jpg", 0));
		when(productRepository.findBySlug("mesa")).thenReturn(Optional.of(mesa));

		CatalogProductDetailDto detail = catalogService.getBySlug("mesa");

		assertEquals(List.of("a.jpg", "b.jpg"), detail.images());
		assertEquals(Availability.AGOTADO, detail.availability());
	}

	@Test
	void slugInexistente_respondeNotFound() {
		when(productRepository.findBySlug("nada")).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> catalogService.getBySlug("nada"));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	@Test
	void destacados_enElOrdenDelAdministradorSinStockExacto() {
		Product primero = product("Primero", "primero", "100", 0, 0);
		Product segundo = product("Segundo", "segundo", "200", 8, 2);
		when(productRepository.findFeatured()).thenReturn(List.of(primero, segundo));

		List<CatalogProductDto> featured = catalogService.featured();

		assertEquals(List.of("primero", "segundo"), featured.stream().map(CatalogProductDto::slug).toList());
		assertEquals(Availability.AGOTADO, featured.get(0).availability());
		assertEquals(Availability.DISPONIBLE, featured.get(1).availability());
	}

	private static Product product(String name, String slug, String price, int stock, int minStock) {
		Product p = new Product();
		p.setName(name);
		p.setSlug(slug);
		p.setPrice(price != null ? new BigDecimal(price) : null);
		p.setStock(stock);
		p.setMinStock(minStock);
		return p;
	}

	private static ProductImage image(Product p, String path, int order) {
		ProductImage image = new ProductImage();
		image.setProduct(p);
		image.setPath(path);
		image.setImageOrder(order);
		return image;
	}
}
