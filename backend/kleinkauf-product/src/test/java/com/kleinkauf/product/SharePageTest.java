package com.kleinkauf.product;

import com.kleinkauf.product.dto.catalog.Availability;
import com.kleinkauf.product.dto.catalog.CatalogProductDetailDto;
import com.kleinkauf.product.services.SharePage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Share link HTML: Open Graph for the preview and redirect to the product page
class SharePageTest {

    private static final String STORE = "http://mitienda.localhost:2999";
    private static final String PUBLIC = "http://localhost:3000";

    private static CatalogProductDetailDto product(String name, String description, BigDecimal price, List<String> images) {
        return new CatalogProductDetailDto(1L, "mesa-de-roble", name, "Mesas", description, price, Availability.DISPONIBLE,
                false, images, null);
    }

    @Test
    void product_withPriceAndImage() {
        String html = SharePage.product(product("Mesa de roble", "Maciza, 6 personas", new BigDecimal("150000.50"),
                List.of("abc.png", "def.png")), STORE, PUBLIC);

        assertThat(html).contains("<meta property=\"og:title\" content=\"Mesa de roble\">");
        assertThat(html).contains("<meta property=\"og:description\" content=\"$ 150.000,50 · Maciza, 6 personas\">");
        // The first image, with an absolute URL (the crawler does not know the gateway)
        assertThat(html).contains("<meta property=\"og:image\" content=\"http://localhost:3000/products/images/abc.png\">");
        assertThat(html).contains("<meta http-equiv=\"refresh\" content=\"0; url=" + STORE + "/productos/mesa-de-roble\">");
    }

    @Test
    void withoutPriceOrImage_doesNotMakeThemUp() {
        String html = SharePage.product(product("Mesa a medida", "", null, List.of()), STORE, PUBLIC);

        assertThat(html).doesNotContain("og:image").doesNotContain("og:description");
        assertThat(html).contains("og:title");
    }

    // The texts are entered by the merchant: they cannot close the attribute or inject a script
    @Test
    void escapesWhatTheMerchantEnters() {
        String html = SharePage.product(product("Mesa \"Roble\" <script>alert(1)</script>", "Con 'comillas' & más",
                null, List.of()), STORE, PUBLIC);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("content=\"Mesa &quot;Roble&quot; &lt;script&gt;alert(1)&lt;/script&gt;\"");
        assertThat(html).contains("Con &#39;comillas&#39; &amp; más");
    }

    @Test
    void longDescription_isTruncated() {
        String html = SharePage.product(product("Mesa", "x".repeat(500), null, List.of()), STORE, PUBLIC);

        assertThat(html).contains("x".repeat(199) + "…");
        assertThat(html).doesNotContain("x".repeat(200));
    }

    @Test
    void productThatDoesNotExist_goesToTheStoreHome() {
        String html = SharePage.store(STORE);

        assertThat(html).contains("url=" + STORE + "/\"").doesNotContain("og:title");
    }
}
