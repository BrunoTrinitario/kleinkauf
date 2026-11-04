package com.kleinkauf.product;

import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundlePricingType;
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
	void listPriceAndAvailableBundles() {
		// Bundle: 1 table + 4 chairs
		Bundle bundle = bundle("90000.00");
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		add(bundle, product(2L, "Silla", "12000.00", 10), 4);

		BundleDto dto = BundleFactory.getBundle(bundle);

		assertEquals(new BigDecimal("90000.00"), dto.getPrice());
		assertEquals(new BigDecimal("98000.00"), dto.getListPrice());
		// Tables are enough for 3, chairs for 2 (10 / 4)
		assertEquals(2, dto.getAvailable());
	}

	@Test
	void productWithoutPrice_noListPrice() {
		Bundle bundle = bundle(null);
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		add(bundle, product(2L, "Silla", null, 10), 4);

		assertNull(BundleFactory.getBundle(bundle).getListPrice());
	}

	@Test
	void withoutStockOfAProduct_noBundlesAvailable() {
		Bundle bundle = bundle(null);
		add(bundle, product(1L, "Mesa", "50000.00", 0), 1);
		add(bundle, product(2L, "Silla", "12000.00", 10), 4);

		assertEquals(0, BundleFactory.getBundle(bundle).getAvailable());
	}

	@Test
	void emptyBundle_noBundlesAvailable() {
		BundleDto dto = BundleFactory.getBundle(bundle(null));

		assertEquals(0, dto.getAvailable());
		assertEquals(BigDecimal.ZERO, dto.getListPrice());
	}

	@Test
	void eachProductCarriesItsPriceAndFirstImage() {
		Bundle bundle = bundle(null);
		Product table = product(1L, "Mesa", "50000.00", 3);
		table.getImages().add(image(table, "mesa-2.png", 1));
		table.getImages().add(image(table, "mesa-1.png", 0));
		add(bundle, table, 1);
		add(bundle, product(2L, "Silla", null, 10), 4);

		BundleDto dto = BundleFactory.getBundle(bundle);

		assertEquals(new BigDecimal("50000.00"), dto.getProducts().get(0).price());
		assertEquals("mesa-1.png", dto.getProducts().get(0).image());
		assertNull(dto.getProducts().get(1).price());
		assertNull(dto.getProducts().get(1).image());
	}

	@Test
	void percentDiscount_overTheRoundedSum() {
		// 1 table 50000 + 4 chairs 12000 = 98000; 15% off = 83300
		Bundle bundle = discounted(BundlePricingType.PERCENT, "15");
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		add(bundle, product(2L, "Silla", "12000.00", 10), 4);
		assertEquals(new BigDecimal("83300.00"), BundleFactory.getBundle(bundle).getPrice());

		// 33.33% of 98000 = 32663.40 off: 65336.60
		bundle.setDiscount(new BigDecimal("33.33"));
		assertEquals(new BigDecimal("65336.60"), BundleFactory.price(bundle));
	}

	@Test
	void percentDiscount_followsTheProductPrice() {
		Bundle bundle = discounted(BundlePricingType.PERCENT, "10");
		Product table = product(1L, "Mesa", "50000.00", 3);
		add(bundle, table, 2);
		assertEquals(new BigDecimal("90000.00"), BundleFactory.price(bundle));

		table.setPrice(new BigDecimal("60000.00"));
		assertEquals(new BigDecimal("108000.00"), BundleFactory.price(bundle));
	}

	@Test
	void fixedDiscount_doesNotGoBelowZero() {
		Bundle bundle = discounted(BundlePricingType.AMOUNT, "8000");
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		assertEquals(new BigDecimal("42000.00"), BundleFactory.price(bundle));

		bundle.setDiscount(new BigDecimal("60000"));
		assertEquals(new BigDecimal("0.00"), BundleFactory.price(bundle));
	}

	@Test
	void withDiscount_productWithoutPriceOrEmptyBundle_noPrice() {
		Bundle bundle = discounted(BundlePricingType.PERCENT, "10");
		assertNull(BundleFactory.price(bundle));

		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);
		add(bundle, product(2L, "Silla", null, 10), 4);
		assertNull(BundleFactory.price(bundle));
	}

	@Test
	void fixedPrice_usesTheStoredOneEvenIfProductsChange() {
		Bundle bundle = bundle("90000.00");
		bundle.setDiscount(new BigDecimal("50"));
		add(bundle, product(1L, "Mesa", "50000.00", 3), 1);

		BundleDto dto = BundleFactory.getBundle(bundle);
		assertEquals(new BigDecimal("90000.00"), dto.getPrice());
		assertEquals(BundlePricingType.FIXED, dto.getPricingType());
	}

	private static Bundle discounted(BundlePricingType type, String discount) {
		Bundle bundle = bundle("1.00");
		bundle.setPricingType(type);
		bundle.setDiscount(new BigDecimal(discount));
		return bundle;
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
