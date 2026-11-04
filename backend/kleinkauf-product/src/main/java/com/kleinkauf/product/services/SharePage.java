package com.kleinkauf.product.services;

import com.kleinkauf.product.dto.catalog.CatalogProductDetailDto;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/*
Minimal HTML of the product share link (docs/backend/products.md): the Open Graph tags for the WhatsApp or
Instagram preview (their crawlers do not run JavaScript) and a redirect to the product page in
the store, which is what a person sees. Stateless: built from what the catalog already returns (and its cache).
 */
public final class SharePage {

    private static final int DESCRIPTION_MAX = 200;

    private SharePage() {
    }

    // storeUrl: the store (http://mitienda.localhost:2999). publicUrl: the gateway, for the image's absolute URL
    public static String product(CatalogProductDetailDto product, String storeUrl, String publicUrl) {
        String target = storeUrl + "/productos/" + product.slug();
        String image = product.images() == null || product.images().isEmpty()
                ? null
                : publicUrl + "/products/images/" + product.images().get(0);
        return page(product.name(), description(product), image, target);
    }

    // Product that does not exist or was deleted: the preview is the store's and it goes to its home page
    public static String store(String storeUrl) {
        return page(null, null, null, storeUrl + "/");
    }

    static String description(CatalogProductDetailDto product) {
        String price = product.price() == null ? null : formatPrice(product.price());
        String text = product.description() == null ? "" : product.description().trim();
        if (text.length() > DESCRIPTION_MAX) {
            text = text.substring(0, DESCRIPTION_MAX - 1).trim() + "…";
        }
        if (price == null) return text;
        return text.isEmpty() ? price : price + " · " + text;
    }

    static String formatPrice(BigDecimal price) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));
        return format.format(price).replace(' ', ' ');
    }

    private static String page(String title, String description, String image, String target) {
        StringBuilder html = new StringBuilder("<!doctype html>\n<html lang=\"es\">\n<head>\n<meta charset=\"utf-8\">\n");
        if (title != null) {
            html.append("<title>").append(escape(title)).append("</title>\n");
            meta(html, "og:title", title);
            meta(html, "og:type", "product");
        }
        if (description != null && !description.isEmpty()) {
            meta(html, "og:description", description);
            html.append("<meta name=\"description\" content=\"").append(escape(description)).append("\">\n");
        }
        if (image != null) {
            meta(html, "og:image", image);
            html.append("<meta name=\"twitter:card\" content=\"summary_large_image\">\n");
        }
        meta(html, "og:url", target);
        String url = escape(target);
        html.append("<meta http-equiv=\"refresh\" content=\"0; url=").append(url).append("\">\n")
                .append("<link rel=\"canonical\" href=\"").append(url).append("\">\n")
                .append("</head>\n<body>\n<p><a href=\"").append(url).append("\">")
                .append(escape(title == null ? target : title)).append("</a></p>\n</body>\n</html>\n");
        return html.toString();
    }

    private static void meta(StringBuilder html, String property, String content) {
        html.append("<meta property=\"").append(property).append("\" content=\"").append(escape(content)).append("\">\n");
    }

    // The texts are entered by the merchant: they are escaped so they cannot close the attribute or inject tags
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
