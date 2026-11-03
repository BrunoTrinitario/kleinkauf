package com.decor.product.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.product.dto.catalog.CatalogProductDetailDto;
import com.decor.product.services.CatalogService;
import com.decor.product.services.SharePage;
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
Link para compartir un producto: el gateway recibe /share/{identificador}/products/{slug}, resuelve la tienda por
el identificador (el crawler no manda Origin) y llega aca como /share/products/{slug} con el schema de la tienda.
Publico y sin JSON: un HTML con Open Graph que redirige a la pagina del producto (ver SharePage).
 */
@RestController
public class ShareController {

    @Autowired
    private CatalogService catalogService;

    // {identifier}: el identificador de la tienda (el schema con - en vez de _)
    @Value("${share.store-url:http://{identifier}.localhost:2999}")
    private String storeUrlTemplate;

    // Gateway visto desde afuera: la imagen de og:image tiene que ser una URL absoluta
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
            // Producto borrado o link mal copiado: 404 para el crawler, la home de la tienda para la persona
            return html(HttpStatus.NOT_FOUND, SharePage.store(storeUrl));
        }
    }

    // Los crawlers guardan la vista previa: una hora alcanza para que un cambio de precio o foto se vea pronto
    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }
}
