package com.kleinkauf.product.services;

import com.kleinkauf.product.dto.catalog.CatalogBundleDto;
import com.kleinkauf.product.dto.catalog.CatalogBundleProductDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductImage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogBundleDtoTest {

	@Test
	void cadaProductoDelComboTraeSuDetalle() {
		Bundle bundle = new Bundle();
		bundle.setId(1L);
		bundle.setName("Comedor");
		bundle.setPrice(new BigDecimal("90000.00"));
		Product mesa = product("mesa", "Mesa", "50000.00");
		mesa.setCategory("Comedor");
		mesa.setDescription("Mesa de roble");
		mesa.getImages().add(image(mesa, "mesa-2.png", 1));
		mesa.getImages().add(image(mesa, "mesa-1.png", 0));
		add(bundle, mesa, 1);
		add(bundle, product("silla", "Silla", null), 4);

		CatalogBundleDto dto = CatalogService.toDto(bundle);

		CatalogBundleProductDto first = dto.products().get(0);
		assertEquals("mesa", first.slug());
		assertEquals(1, first.quantity());
		assertEquals("Comedor", first.category());
		assertEquals("Mesa de roble", first.description());
		assertEquals(new BigDecimal("50000.00"), first.price());
		assertEquals("mesa-1.png", first.image());

		CatalogBundleProductDto second = dto.products().get(1);
		assertEquals(4, second.quantity());
		assertNull(second.price());
		assertNull(second.image());
	}

	private static Product product(String slug, String name, String price) {
		Product product = new Product();
		product.setSlug(slug);
		product.setName(name);
		product.setPrice(price != null ? new BigDecimal(price) : null);
		product.setStock(10);
		return product;
	}

	private static ProductImage image(Product product, String path, int order) {
		ProductImage image = new ProductImage();
		image.setProduct(product);
		image.setPath(path);
		image.setImageOrder(order);
		return image;
	}

	private static void add(Bundle bundle, Product product, int quantity) {
		BundleProduct bp = new BundleProduct();
		bp.setBundle(bundle);
		bp.setProduct(product);
		bp.setQuantity(quantity);
		bundle.getProducts().add(bp);
	}
}
