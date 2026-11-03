package com.kleinkauf.product;

import com.kleinkauf.product.dto.csv.ImportResultDto;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.repositories.ProductMetaDataRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.services.CatalogCacheService;
import com.kleinkauf.product.services.ProductCsvService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Importacion real contra H2 (modo PostgreSQL): consulta nativa, metadatos y soft delete
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:csv;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ProductCsvService.class)
class ProductCsvIntegrationTest {

	@Autowired
	private ProductCsvService service;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMetaDataRepository productMetaDataRepository;

	@Autowired
	private TestEntityManager em;

	// Redis no esta en el test
	@MockitoBean
	private CatalogCacheService catalogCache;

	@Test
	void importaYActualizaContraLaBase() {
		Product silla = new Product();
		silla.setName("Silla");
		silla.setCategory("living");
		silla.setPrice(new BigDecimal("10000.00"));
		em.persistAndFlush(silla);

		ImportResultDto result = service.importCsv(csv(
				"nombre;categoria;precio;material;alto",
				"Mesa;comedor;150.000,50;Roble;75",
				"Silla;;12.500;;"));

		assertFalse(result.hasErrors(), result.errors().toString());
		assertEquals(1, result.created());
		assertEquals(1, result.updated());
		em.flush();
		em.clear();

		Product mesa = productRepository.findByName("Mesa").orElseThrow();
		assertEquals(new BigDecimal("150000.50"), mesa.getPrice());
		assertEquals("Roble", productMetaDataRepository.findByProduct_Id(mesa.getId()).orElseThrow().getMaterial());

		Product sillaActualizada = productRepository.findByName("Silla").orElseThrow();
		assertEquals(new BigDecimal("12500.00"), sillaActualizada.getPrice());
		assertEquals("living", sillaActualizada.getCategory());
	}

	@Test
	void elNombreDeUnProductoBorradoNoSePuedeUsar() {
		Product lampara = new Product();
		lampara.setName("Lampara");
		em.persistAndFlush(lampara);
		productRepository.deleteById(lampara.getId());
		em.flush();
		em.clear();

		assertTrue(productRepository.existsByNameIncludingDeleted("Lampara"));
		assertFalse(productRepository.existsByNameIncludingDeleted("Mesa"));

		ImportResultDto result = service.importCsv(csv("nombre", "Lampara"));

		assertTrue(result.hasErrors());
		assertEquals(0, productRepository.count());
	}

	private static MockMultipartFile csv(String header, String... rows) {
		String content = "﻿" + header + "\r\n" + String.join("\r\n", rows) + "\r\n";
		return new MockMultipartFile("file", content.getBytes(StandardCharsets.UTF_8));
	}
}
