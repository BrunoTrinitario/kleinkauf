package com.kleinkauf.product.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.dto.catalog.CatalogProductDetailDto;
import com.kleinkauf.product.services.CatalogService;
import com.kleinkauf.product.services.SharePage;
import com.kleinkauf.product.services.Sitemap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/*
What crawlers read without Origin, so the gateway resolves the store by the identifier in the path:
- Link to share a product: /share/{identifier}/products/{slug} arrives here as /share/products/{slug} with the store's
  schema. Public and not JSON: an HTML page with Open Graph that redirects to the product page (see SharePage).
- The store's sitemap: /share/{identifier}/sitemap.xml arrives as /share/sitemap.xml. The store's frontend serves it
  at <store>/sitemap.xml (nginx asks the gateway), which is where robots.txt points.
 */
@RestController
public class ShareController {

    @Autowired
    private CatalogService catalogService;

    // {identifier}: the store identifier (the schema with - instead of _)
    @Value("${share.store-url:http://{identifier}.localhost:2999}")
    private String storeUrlTemplate;

    // Gateway as seen from outside: the og:image must be an absolute URL
    @Value("${share.public-url:http://localhost:3000}")
    private String publicUrl;

    @GetMapping(value = "/share/products/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> product(@PathVariable String slug) throws ApiException {
        String storeUrl = storeUrlTemplate.replace("{identifier}", TenantContext.getCurrentTenant().replace('_', '-'));
        try {
            CatalogProductDetailDto product = catalogService.getBySlug(slug);
            return html(HttpStatus.OK, SharePage.product(product, storeUrl, publicUrl));
        } catch (ApiException e) {
            if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            // Deleted product or badly copied link: 404 for the crawler, the store's home page for the person
            return html(HttpStatus.NOT_FOUND, SharePage.store(storeUrl));
        }
    }

    @GetMapping(value = "/share/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String storeUrl = storeUrlTemplate.replace("{identifier}", TenantContext.getCurrentTenant().replace('_', '-'));
        String xml = Sitemap.build(storeUrl, catalogService.productSlugs(), !catalogService.bundles().isEmpty());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType(MediaType.APPLICATION_XML, java.nio.charset.StandardCharsets.UTF_8))
                .body(xml);
    }

    // Crawlers cache the preview: one hour is enough for a price or photo change to show soon
    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }
}
