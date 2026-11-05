package com.kleinkauf.product;

import com.kleinkauf.product.config.SlugBackfill;
import com.kleinkauf.product.dto.catalog.Availability;
import com.kleinkauf.product.util.Slugs;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlugsTest {

	@Test
	void slugify() {
		assertEquals("mesa-de-roble", Slugs.slugify("Mesa de Roble"));
		assertEquals("silla-rustica-2-m", Slugs.slugify("  Silla Rústica (2 m)  "));
		assertEquals("lampara-ninos", Slugs.slugify("Lámpara ñiños"));
		assertEquals("producto", Slugs.slugify("¡¡¡"));
		assertEquals("producto", Slugs.slugify(null));
		assertTrue(Slugs.slugify("a ".repeat(100)).length() <= 80);
	}

	@Test
	void ifItAlreadyExistsAddsANumber() {
		Set<String> used = Set.of("mesa", "mesa-2");

		assertEquals("mesa-3", Slugs.unique("Mesa", used::contains));
		assertEquals("silla", Slugs.unique("Silla", used::contains));
	}

	@Test
	void availabilityWithoutExactStock() {
		assertEquals(Availability.AGOTADO, Availability.of(0, 3));
		assertEquals(Availability.POCAS_UNIDADES, Availability.of(3, 3));
		assertEquals(Availability.POCAS_UNIDADES, Availability.of(1, 3));
		assertEquals(Availability.DISPONIBLE, Availability.of(4, 3));
		// Without minimum stock it is never 'pocas unidades'
		assertEquals(Availability.DISPONIBLE, Availability.of(1, 0));
	}

	@Test
	void backfill_fillsMissingSlugsWithoutRepeating() throws Exception {
		DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:backfill;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
			st.execute("CREATE SCHEMA tienda_a");
			st.execute("CREATE TABLE tienda_a.product (id BIGINT PRIMARY KEY, name VARCHAR(255), slug VARCHAR(100))");
			st.execute("INSERT INTO tienda_a.product VALUES (1, 'Mesa', 'mesa'), (2, 'MESA', NULL), (3, 'Silla Rústica', NULL)");
		}

		int filled = SlugBackfill.fill(ds, "tienda_a");
		int again = SlugBackfill.fill(ds, "tienda_a");

		assertEquals(2, filled);
		assertEquals(0, again);
		List<String> slugs = new ArrayList<>();
		try (Connection conn = ds.getConnection(); Statement st = conn.createStatement();
			 ResultSet rs = st.executeQuery("SELECT slug FROM tienda_a.product ORDER BY id")) {
			while (rs.next()) slugs.add(rs.getString(1));
		}
		assertEquals(List.of("mesa", "mesa-2", "silla-rustica"), slugs);
	}
}
