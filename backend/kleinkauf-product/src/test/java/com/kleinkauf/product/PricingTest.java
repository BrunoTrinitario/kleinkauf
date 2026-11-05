package com.kleinkauf.product;

import static org.mockito.Mockito.verify;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.dto.PricingDto;
import com.kleinkauf.product.dto.UpdatePricingDto;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.services.CatalogCacheService;
import com.kleinkauf.product.services.ProductService;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CatalogCacheService catalogCache;
	@Mock
	private AuditLog auditLog;


	@InjectMocks
	private ProductService productService;

	@Test
	void computesTheMarginOverTheSalePrice() throws ApiException {
		mockProduct("15000.00", "9000.00");

		PricingDto pricing = productService.getPricing(1L);

		assertEquals(new BigDecimal("6000.00"), pricing.margin());
		assertEquals(new BigDecimal("40.00"), pricing.marginPercent());
		// Seeing the cost is a sensitive read
		verify(auditLog).read("ProductPricing", 1L);
	}

	@Test
	void negativeMargin_whenSoldBelowCost() throws ApiException {
		mockProduct("800.00", "1000.00");

		PricingDto pricing = productService.getPricing(1L);

		assertEquals(new BigDecimal("-200.00"), pricing.margin());
		assertEquals(new BigDecimal("-25.00"), pricing.marginPercent());
	}

	@Test
	void withoutCost_noMargin() throws ApiException {
		mockProduct("1000.00", null);

		PricingDto pricing = productService.getPricing(1L);

		assertEquals(new BigDecimal("1000.00"), pricing.price());
		assertNull(pricing.margin());
		assertNull(pricing.marginPercent());
	}

	@Test
	void zeroPrice_doesNotDivide() throws ApiException {
		mockProduct("0", "100");

		PricingDto pricing = productService.getPricing(1L);

		assertEquals(new BigDecimal("-100"), pricing.margin());
		assertNull(pricing.marginPercent());
	}

	@Test
	void negativePrice_respondsBadRequest() {
		ApiException ex = assertThrows(ApiException.class,
				() -> productService.updatePricing(1L, new UpdatePricingDto(new BigDecimal("-1"), BigDecimal.TEN)));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verifyNoInteractions(productRepository);
	}

	@Test
	void updatesPriceAndCost() throws ApiException {
		Product product = mockProduct(null, null);

		PricingDto pricing = productService.updatePricing(1L, new UpdatePricingDto(new BigDecimal("500.00"), new BigDecimal("300.00")));

		assertEquals(new BigDecimal("500.00"), product.getPrice());
		assertEquals(new BigDecimal("300.00"), product.getCost());
		assertEquals(new BigDecimal("40.00"), pricing.marginPercent());
	}

	private Product mockProduct(String price, String cost) {
		Product product = new Product();
		product.setId(1L);
		product.setPrice(price != null ? new BigDecimal(price) : null);
		product.setCost(cost != null ? new BigDecimal(cost) : null);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));
		return product;
	}
}
