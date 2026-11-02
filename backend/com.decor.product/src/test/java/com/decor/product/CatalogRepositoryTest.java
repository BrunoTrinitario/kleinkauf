package com.decor.product;

import com.decor.product.dto.catalog.CatalogSort;
import com.decor.product.dto.catalog.CategoryDto;
import com.decor.product.entities.Product;
import com.decor.product.repositories.CatalogRepository;
import com.decor.product.repositories.CatalogRepository.Filter;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.util.Slugs;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Consultas del catalogo contra H2 (modo PostgreSQL)
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(CatalogRepository.class)
class CatalogRepositoryTest {

	private static final Filter ALL = new Filter(null, null);

	@Autowired
	private CatalogRepository catalogRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private TestEntityManager em;

	@BeforeEach
	void productos() {
		persist("Mesa de roble", "comedor", "Mesa rústica para 6", "150000");
		persist("Silla", "comedor", "Silla de madera", "20000");
		persist("Lampara", "iluminacion", "Lampara 100% algodon", null);
		persist("Alfombra", "living", "Alfombra tejida", "50000");
		Product borrado = persist("Banco", "comedor", "Banco", "1000");
		em.flush();
		productRepository.deleteById(borrado.getId());
		em.flush();
		em.clear();
	}

	@Test
	void porNombre_sinLosBorrados() {
		assertEquals(List.of("Alfombra", "Lampara", "Mesa de roble", "Silla"), names(ALL, CatalogSort.NAME));
		assertEquals(4, catalogRepository.count(ALL));
	}

	@Test
	void porPrecio_losConsultarAlFinalEnAmbasDirecciones() {
		assertEquals(List.of("Silla", "Alfombra", "Mesa de roble", "Lampara"), names(ALL, CatalogSort.PRICE_ASC));
		assertEquals(List.of("Mesa de roble", "Alfombra", "Silla", "Lampara"), names(ALL, CatalogSort.PRICE_DESC));
	}

	@Test
	void busquedaEnNombreDescripcionYCategoriaSinMayusculas() {
		assertEquals(List.of("Silla"), names(new Filter("MADERA", null), CatalogSort.NAME));
		assertEquals(List.of("Mesa de roble", "Silla"), names(new Filter("comedor", null), CatalogSort.NAME));
		assertEquals(List.of("Mesa de roble"), names(new Filter("ROBLE", null), CatalogSort.NAME));
		// '%' se busca tal cual, no como comodin: solo la lampara tiene '%' en la descripcion
		assertEquals(List.of("Lampara"), names(new Filter("100%", null), CatalogSort.NAME));
		assertEquals(List.of("Lampara"), names(new Filter("%", null), CatalogSort.NAME));
		// Sin acentos no encuentra 'rústica' (decision: la busqueda distingue acentos)
		assertEquals(List.of(), names(new Filter("rustica", null), CatalogSort.NAME));
	}

	@Test
	void filtroPorCategoria() {
		assertEquals(List.of("Mesa de roble", "Silla"), names(new Filter(null, "Comedor"), CatalogSort.NAME));
		assertEquals(2, catalogRepository.count(new Filter(null, "comedor")));
		assertEquals(List.of("Silla"), names(new Filter("silla", "comedor"), CatalogSort.NAME));
	}

	@Test
	void paginacion() {
		assertEquals(List.of("Alfombra", "Lampara"), catalogRepository.search(ALL, CatalogSort.NAME, 0, 2).stream().map(Product::getName).toList());
		assertEquals(List.of("Mesa de roble", "Silla"), catalogRepository.search(ALL, CatalogSort.NAME, 1, 2).stream().map(Product::getName).toList());
		assertTrue(catalogRepository.search(ALL, CatalogSort.NAME, 2, 2).isEmpty());
	}

	@Test
	void categoriasConCantidad_sinLosBorrados() {
		assertEquals(List.of(
				new CategoryDto("comedor", 2),
				new CategoryDto("iluminacion", 1),
				new CategoryDto("living", 1)
		), catalogRepository.categories());
	}

	@Test
	void buscarPorSlug() {
		assertEquals("Silla", productRepository.findBySlug("silla").orElseThrow().getName());
		assertTrue(productRepository.findBySlug("banco").isEmpty());
	}

	private List<String> names(Filter filter, CatalogSort sort) {
		return catalogRepository.search(filter, sort, 0, 24).stream().map(Product::getName).toList();
	}

	private Product persist(String name, String category, String description, String price) {
		Product p = new Product();
		p.setName(name);
		p.setSlug(Slugs.slugify(name));
		p.setCategory(category);
		p.setDescription(description);
		p.setPrice(price != null ? new BigDecimal(price) : null);
		return em.persist(p);
	}
}
