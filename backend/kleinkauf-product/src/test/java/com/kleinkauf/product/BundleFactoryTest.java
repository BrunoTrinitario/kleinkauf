package com.kleinkauf.product;

import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductImage;
import com.kleinkauf.product.factories.BundleFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BundleFactoryTest {

	@Test
	void precioDeListaYCombosDisponibles() {
		// Combo: 1 mesa + 4 sillas
		Bundle bundle = bundle("90000.00");
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		add(bundle, product(2L, "Silla", "12000.00", 10), 4);

		BundleDto dto = BundleFactory.getBundle(bundle);

		assertEquals(new BigDecimal("90000.00"), dto.getPrice());
		assertEquals(new BigDecimal("98000.00"), dto.getListPrice());
		// Mesas alcanzan para 3, sillas para 2 (10 / 4)
		assertEquals(2, dto.getAvailable());
	}

	@Test
	void productoSinPrecio_noHayPrecioDeLista() {
		Bundle bundle = bundle(null);
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		add(bundle, product(2L, "Silla", null, 10), 4);

		assertNull(BundleFactory.getBundle(bundle).getListPrice());
	}

	@Test
	void sinStockDeUnProducto_noHayCombosDisponibles() {
		Bundle bundle = bundle(null);
		add(bundle, product(1L, "Mesa", "50000.00", 0), 1);
		add(bundle, product(2L, "Silla", "12000.00", 10), 4);

		assertEquals(0, BundleFactory.getBundle(bundle).getAvailable());
	}

	@Test
	void comboVacio_noHayCombosDisponibles() {
		BundleDto dto = BundleFactory.getBundle(bundle(null));

		assertEquals(0, dto.getAvailable());
		assertEquals(BigDecimal.ZERO, dto.getListPrice());
	}

	@Test
	void cadaProductoTraeSuPrecioYSuPrimeraImagen() {
		Bundle bundle = bundle(null);
		Product mesa = product(1L, "Mesa", "50000.00", 3);
		mesa.getImages().add(image(mesa, "mesa-2.png", 1));
		mesa.getImages().add(image(mesa, "mesa-1.png", 0));
		add(bundle, mesa, 1);
		add(bundle, product(2L, "Silla", null, 10), 4);

		BundleDto dto = BundleFactory.getBundle(bundle);

		assertEquals(new BigDecimal("50000.00"), dto.getProducts().get(0).price());
		assertEquals("mesa-1.png", dto.getProducts().get(0).image());
		assertNull(dto.getProducts().get(1).price());
		assertNull(dto.getProducts().get(1).image());
	}

	private static ProductImage image(Product product, String path, int order) {
		ProductImage image = new ProductImage();
		image.setProduct(product);
		image.setPath(path);
		image.setImageOrder(order);
		return image;
	}

	private static Bundle bundle(String price) {
		Bundle bundle = new Bundle();
		bundle.setId(1L);
		bundle.setName("Comedor");
		bundle.setPrice(price != null ? new BigDecimal(price) : null);
		return bundle;
	}

	private static Product product(Long id, String name, String price, int stock) {
		Product product = new Product();
		product.setId(id);
		product.setName(name);
		product.setPrice(price != null ? new BigDecimal(price) : null);
		product.setStock(stock);
		return product;
	}

	private static void add(Bundle bundle, Product product, int quantity) {
		BundleProduct bp = new BundleProduct();
		bp.setBundle(bundle);
		bp.setProduct(product);
		bp.setQuantity(quantity);
		bundle.getProducts().add(bp);
	}
}
