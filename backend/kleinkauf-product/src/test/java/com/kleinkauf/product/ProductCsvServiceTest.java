package com.kleinkauf.product;

import java.util.Map;
import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.product.dto.csv.ImportResultDto;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductMetaData;
import com.kleinkauf.product.repositories.ProductMetaDataRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.services.CatalogCacheService;
import com.kleinkauf.product.services.ProductCsvService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCsvServiceTest {

	private static final String HEADER = "nombre;categoria;descripcion;precio;costo;material;color;estilo;alto;ancho;largo;peso";

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductMetaDataRepository productMetaDataRepository;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private CatalogCacheService catalogCache;
	@Mock
	private AuditLog auditLog;


	@InjectMocks
	private ProductCsvService service;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(productRepository.findAll()).thenReturn(List.of());
	}

	// Exportar

	@Test
	void exporta_conBomPuntoYComaYComaDecimal() {
		Product mesa = product("Mesa", "comedor", "Mesa de roble; 6 personas", "150000.50", "90000");
		ProductMetaData md = new ProductMetaData();
		md.setMaterial("Roble");
		md.setHeight(75.5);
		mesa.setMetaData(md);
		when(productRepository.findAllByOrderByNameAsc()).thenReturn(List.of(mesa, product("Silla", null, null, null, null)));

		String csv = new String(service.exportCsv(), StandardCharsets.UTF_8);

		assertTrue(csv.startsWith("﻿"));
		String[] lines = csv.substring(1).split("\r\n");
		assertEquals(HEADER, lines[0]);
		// La descripcion con ";" va entre comillas. Color y estilo sin cargar quedan vacios
		assertEquals("Mesa;comedor;\"Mesa de roble; 6 personas\";150000,50;90000;Roble;;;75,5;0;0;0", lines[1]);
		assertEquals("Silla;;;;;;;;;;;", lines[2]);
	}

	@Test
	void plantilla_soloEncabezados() {
		String csv = new String(service.template(), StandardCharsets.UTF_8);

		assertEquals("﻿" + HEADER + "\r\n", csv);
	}

	// Importar

	@Test
	void importa_creaNuevosConStockCeroYMetadatos() {
		ImportResultDto result = service.importCsv(csv(HEADER,
				"Mesa;comedor;Mesa de roble;150.000,50;90000;Roble;Natural;Rústico;75,5;180;90;40"));

		assertEquals(1, result.created());
		assertFalse(result.hasErrors());
		ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(saved.capture());
		Product mesa = saved.getValue();
		assertEquals("Mesa", mesa.getName());
		assertEquals(new BigDecimal("150000.50"), mesa.getPrice());
		assertEquals(new BigDecimal("90000"), mesa.getCost());
		assertEquals(0, mesa.getStock());

		ArgumentCaptor<ProductMetaData> md = ArgumentCaptor.forClass(ProductMetaData.class);
		verify(productMetaDataRepository).save(md.capture());
		assertEquals("Rústico", md.getValue().getStyle());
		assertEquals(75.5, md.getValue().getHeight());
		assertEquals(40.0, md.getValue().getWeight());

		// Una sola entrada con el resumen
		verify(auditLog).record(AuditEvent.IMPORT, "Product", null, Map.of(
				"created", new AuditEvent.Change(null, "1"),
				"updated", new AuditEvent.Change(null, "0")));
	}

	@Test
	void importa_actualizaExistentesYLaCeldaVaciaNoCambiaNada() {
		Product silla = product("Silla", "living", "Silla moderna", "10000", "6000");
		silla.setId(3L);
		when(productRepository.findAll()).thenReturn(List.of(silla));

		// Solo nombre y precio: actualizacion masiva de precios
		ImportResultDto result = service.importCsv(csv("nombre;precio;costo", "Silla;12.500;"));

		assertEquals(1, result.updated());
		assertEquals(new BigDecimal("12500"), silla.getPrice());
		assertEquals(new BigDecimal("6000"), silla.getCost());
		assertEquals("living", silla.getCategory());
		assertEquals("Silla moderna", silla.getDescription());
		verify(productMetaDataRepository, never()).save(any());
	}

	@Test
	void importa_actualizaMetadatosExistentes() {
		Product silla = product("Silla", null, null, null, null);
		silla.setId(3L);
		ProductMetaData md = new ProductMetaData();
		md.setMaterial("Pino");
		md.setColor("Blanco");
		when(productRepository.findAll()).thenReturn(List.of(silla));
		when(productMetaDataRepository.findByProduct_Id(3L)).thenReturn(Optional.of(md));

		service.importCsv(csv("nombre;color", "Silla;Negro"));

		assertEquals("Negro", md.getColor());
		assertEquals("Pino", md.getMaterial());
		verify(productMetaDataRepository).save(md);
	}

	@Test
	void encabezadosConAcentosYMayusculas() {
		ImportResultDto result = service.importCsv(csv(" Nombre ;Categoría;DESCRIPCIÓN", "Mesa;comedor;Mesa"));

		assertFalse(result.hasErrors(), result.errors().toString());
		assertEquals(1, result.created());
	}

	@Test
	void columnaDesconocida_esErrorYNoSeImportaNada() {
		ImportResultDto result = service.importCsv(csv("nombre;precioo", "Mesa;100"));

		assertError(result, 1, "Unknown column: 'precioo'");
	}

	@Test
	void sinColumnaNombre_esError() {
		ImportResultDto result = service.importCsv(csv("precio", "100"));

		assertError(result, 1, "The 'nombre' column is required");
	}

	@Test
	void todoONada_unErrorEnUnaFilaFrenaTodo() {
		ImportResultDto result = service.importCsv(csv("nombre;precio",
				"Mesa;100",
				"Silla;15.5",
				";200",
				"Mesa;300",
				"Banco;-5"));

		assertEquals(0, result.created());
		assertEquals(List.of(3, 4, 5, 6), result.errors().stream().map(ImportResultDto.RowError::row).toList());
		assertTrue(result.errors().get(0).message().contains("not a valid number"));
		assertTrue(result.errors().get(1).message().contains("name is required"));
		assertTrue(result.errors().get(2).message().contains("repeated"));
		assertTrue(result.errors().get(3).message().contains("zero or greater"));
		verify(productRepository, never()).save(any());
		verify(transactionManager, never()).getTransaction(any());
	}

	@Test
	void precioConMasDeDosDecimales_esError() {
		ImportResultDto result = service.importCsv(csv("nombre;precio", "Mesa;100,555"));

		assertTrue(result.hasErrors());
		assertTrue(result.errors().get(0).message().contains("decimals"));
	}

	@Test
	void textoMasLargoQue255_esError() {
		ImportResultDto result = service.importCsv(csv("nombre;descripcion", "Mesa;" + "x".repeat(256)));

		assertTrue(result.hasErrors());
	}

	@Test
	void nombreDeUnProductoBorrado_esError() {
		when(productRepository.existsByNameIncludingDeleted("Mesa")).thenReturn(true);

		ImportResultDto result = service.importCsv(csv("nombre", "Mesa"));

		assertError(result, 2, "The name 'Mesa' belongs to a deleted product");
	}

	@Test
	void masDe5000Filas_esError() {
		String[] rows = new String[ProductCsvService.MAX_ROWS + 1];
		for (int i = 0; i < rows.length; i++) rows[i] = "P" + i;

		ImportResultDto result = service.importCsv(csv("nombre", rows));

		assertError(result, 0, "The file has more than 5000 products");
	}

	@Test
	void archivoVacio_esError() {
		ImportResultDto result = service.importCsv(new MockMultipartFile("file", new byte[0]));

		assertError(result, 0, "The file is empty");
	}

	@Test
	void aceptaElBomYLaDescripcionConSaltosDeLinea() {
		String content = "﻿" + "nombre;descripcion\r\n" + "Mesa;\"Linea 1\nLinea 2\"\r\n";

		ImportResultDto result = service.importCsv(new MockMultipartFile("file", content.getBytes(StandardCharsets.UTF_8)));

		assertFalse(result.hasErrors(), result.errors().toString());
		ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(saved.capture());
		assertEquals("Linea 1\nLinea 2", saved.getValue().getDescription());
	}

	@Test
	void seExportaYSeVuelveAImportarIgual() {
		Product mesa = product("Mesa", "comedor", "Mesa de roble", "150000.50", "90000.00");
		when(productRepository.findAllByOrderByNameAsc()).thenReturn(List.of(mesa));
		byte[] exported = service.exportCsv();

		ImportResultDto result = service.importCsv(new MockMultipartFile("file", exported));

		assertFalse(result.hasErrors(), result.errors().toString());
		ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(saved.capture());
		assertEquals(new BigDecimal("150000.50"), saved.getValue().getPrice());
		assertNull(saved.getValue().getMetaData());
	}

	private static void assertError(ImportResultDto result, int row, String message) {
		assertEquals(1, result.errors().size(), result.errors().toString());
		assertEquals(row, result.errors().get(0).row());
		assertEquals(message, result.errors().get(0).message());
		assertEquals(0, result.created());
		assertEquals(0, result.updated());
	}

	private static MockMultipartFile csv(String header, String... rows) {
		String content = header + "\r\n" + String.join("\r\n", rows) + "\r\n";
		return new MockMultipartFile("file", "productos.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
	}

	private static Product product(String name, String category, String description, String price, String cost) {
		Product product = new Product();
		product.setName(name);
		product.setCategory(category);
		product.setDescription(description);
		product.setPrice(price != null ? new BigDecimal(price) : null);
		product.setCost(cost != null ? new BigDecimal(cost) : null);
		return product;
	}
}
