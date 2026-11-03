package com.decor.product;

import com.decor.product.dto.catalog.Availability;
import com.decor.product.dto.catalog.CatalogProductDetailDto;
import com.decor.product.services.SharePage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// HTML del link para compartir: Open Graph para la vista previa y redireccion a la pagina del producto
class SharePageTest {

    private static final String STORE = "http://mitienda.localhost:2999";
    private static final String PUBLIC = "http://localhost:3000";

    private static CatalogProductDetailDto product(String name, String description, BigDecimal price, List<String> images) {
        return new CatalogProductDetailDto(1L, "mesa-de-roble", name, "Mesas", description, price, Availability.DISPONIBLE,
                false, images, null);
    }

    @Test
    void producto_conPrecioEImagen() {
        String html = SharePage.product(product("Mesa de roble", "Maciza, 6 personas", new BigDecimal("150000.50"),
                List.of("abc.png", "def.png")), STORE, PUBLIC);

        assertThat(html).contains("<meta property=\"og:title\" content=\"Mesa de roble\">");
        assertThat(html).contains("<meta property=\"og:description\" content=\"$ 150.000,50 · Maciza, 6 personas\">");
        // La primera imagen, con URL absoluta (el crawler no conoce el gateway)
        assertThat(html).contains("<meta property=\"og:image\" content=\"http://localhost:3000/products/images/abc.png\">");
        assertThat(html).contains("<meta http-equiv=\"refresh\" content=\"0; url=" + STORE + "/productos/mesa-de-roble\">");
    }

    @Test
    void sinPrecioNiImagen_noLasInventa() {
        String html = SharePage.product(product("Mesa a medida", "", null, List.of()), STORE, PUBLIC);

        assertThat(html).doesNotContain("og:image").doesNotContain("og:description");
        assertThat(html).contains("og:title");
    }

    // Los textos los carga el comercio: no pueden cerrar el atributo ni meter un script
    @Test
    void escapaLoQueCargaElComercio() {
        String html = SharePage.product(product("Mesa \"Roble\" <script>alert(1)</script>", "Con 'comillas' & más",
                null, List.of()), STORE, PUBLIC);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("content=\"Mesa &quot;Roble&quot; &lt;script&gt;alert(1)&lt;/script&gt;\"");
        assertThat(html).contains("Con &#39;comillas&#39; &amp; más");
    }

    @Test
    void descripcionLarga_seRecorta() {
        String html = SharePage.product(product("Mesa", "x".repeat(500), null, List.of()), STORE, PUBLIC);

        assertThat(html).contains("x".repeat(199) + "…");
        assertThat(html).doesNotContain("x".repeat(200));
    }

    @Test
    void productoQueNoExiste_vaALaHomeDeLaTienda() {
        String html = SharePage.store(STORE);

        assertThat(html).contains("url=" + STORE + "/\"").doesNotContain("og:title");
    }
}
