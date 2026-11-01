package com.decor.product;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.dto.FeaturedDto;
import com.decor.product.dto.LightProductDto;
import com.decor.product.entities.Product;
import com.decor.product.factories.ProductFactory;
import com.decor.product.repositories.ProductImageRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.services.ProductService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeaturedTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductImageRepository productImageRepository;

	@Mock
	private ProductFactory productFactory;

	@InjectMocks
	private ProductService productService;

	@Test
	void marcarComoDestacadoConOrden() throws ApiException {
		Product product = mockProduct();

		productService.setFeatured(1L, new FeaturedDto(true, 2));

		assertTrue(product.isFeatured());
		assertEquals(2, product.getFeaturedOrder());
		verify(productRepository).save(product);
	}

	@Test
	void desmarcar_borraElOrden() throws ApiException {
		Product product = mockProduct();
		product.setFeatured(true);
		product.setFeaturedOrder(1);

		productService.setFeatured(1L, new FeaturedDto(false, 5));

		assertFalse(product.isFeatured());
		assertNull(product.getFeaturedOrder());
	}

	@Test
	void ordenNegativo_respondeBadRequest() {
		ApiException ex = assertThrows(ApiException.class, () -> productService.setFeatured(1L, new FeaturedDto(true, -1)));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verify(productRepository, never()).save(any());
	}

	@Test
	void productoInexistente_respondeNotFound() {
		when(productRepository.findById(1L)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> productService.setFeatured(1L, new FeaturedDto(true, null)));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	@Test
	void listaDeDestacados_respetaElOrdenDelRepositorio() throws ApiException {
		Product primero = product(1L);
		Product segundo = product(2L);
		when(productRepository.findFeatured()).thenReturn(List.of(primero, segundo));
		when(productFactory.getLightProductInfo(any(Product.class), anyList())).thenAnswer(invocation -> {
			Product p = invocation.getArgument(0);
			return new LightProductDto(p.getId(), "P" + p.getId(), null, null, 0, null, true, List.of());
		});

		List<Long> ids = productService.getFeaturedProducts().stream().map(LightProductDto::id).toList();

		assertEquals(List.of(1L, 2L), ids);
	}

	private Product mockProduct() {
		Product product = product(1L);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));
		return product;
	}

	private static Product product(Long id) {
		Product product = new Product();
		product.setId(id);
		return product;
	}
}
