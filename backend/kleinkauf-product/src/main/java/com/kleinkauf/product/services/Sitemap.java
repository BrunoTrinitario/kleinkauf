package com.kleinkauf.product.services;

import java.util.ArrayList;
import java.util.List;

/*
The store's sitemap.xml for search engines (https://www.sitemaps.org/protocol.html): the public pages of the
storefront and one entry per product page. The panel, the cart, the checkout and the account are left out
(robots.txt blocks them too). Absolute URLs on the store's own host.
 */
public final class Sitemap {

    // Limit of the protocol per file; a store with more products would need a sitemap index
    static final int MAX_URLS = 50_000;

    private Sitemap() {
    }

    // storeUrl: the store without a trailing slash (https://mitienda.kleinkauf.site). hasBundles: whether /combos has
    // something to show
    public static String build(String storeUrl, List<String> productSlugs, boolean hasBundles) {
        List<String[]> urls = new ArrayList<>();
        urls.add(new String[]{"/", "daily", "1.0"});
        urls.add(new String[]{"/catalogo", "daily", "0.9"});
        if (hasBundles) {
            urls.add(new String[]{"/combos", "weekly", "0.7"});
        }
        for (String slug : productSlugs) {
            if (slug == null || slug.isBlank()) continue;
            urls.add(new String[]{"/productos/" + slug, "weekly", "0.8"});
        }
        urls.add(new String[]{"/privacidad", "yearly", "0.1"});
        urls.add(new String[]{"/terminos", "yearly", "0.1"});

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String[] url : urls.subList(0, Math.min(urls.size(), MAX_URLS))) {
            xml.append("  <url><loc>").append(escape(storeUrl + url[0])).append("</loc>")
                    .append("<changefreq>").append(url[1]).append("</changefreq>")
                    .append("<priority>").append(url[2]).append("</priority></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
