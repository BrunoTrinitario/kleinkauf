package com.kleinkauf.product;

import com.kleinkauf.product.services.Sitemap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SitemapTest {

    @Test
    void publicPagesAndOneEntryPerProduct_onTheStoreHost() {
        String xml = Sitemap.build("https://casa-roble.kleinkauf.site", Arrays.asList("mesa-de-roble", null, "silla"), true);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")
                .contains("<loc>https://casa-roble.kleinkauf.site/</loc>")
                .contains("<loc>https://casa-roble.kleinkauf.site/catalogo</loc>")
                .contains("<loc>https://casa-roble.kleinkauf.site/combos</loc>")
                .contains("<loc>https://casa-roble.kleinkauf.site/productos/mesa-de-roble</loc>")
                .contains("<loc>https://casa-roble.kleinkauf.site/productos/silla</loc>")
                .contains("<loc>https://casa-roble.kleinkauf.site/privacidad</loc>")
                // Nothing private: the panel, the cart or the account
                .doesNotContain("/admin").doesNotContain("/carrito").doesNotContain("/checkout");
        // A product without a slug does not leave an empty URL
        assertThat(xml).doesNotContain("/productos/<");
    }

    @Test
    void withoutBundles_combosIsLeftOut_andSpecialCharactersAreEscaped() {
        String xml = Sitemap.build("https://tienda.kleinkauf.site", java.util.List.of("a&b"), false);

        assertThat(xml).doesNotContain("/combos").contains("/productos/a&amp;b");
    }
}
