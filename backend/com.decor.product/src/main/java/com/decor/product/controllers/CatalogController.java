package com.decor.product.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.dto.catalog.CatalogBundleDto;
import com.decor.product.dto.catalog.CatalogPageDto;
import com.decor.product.dto.catalog.CatalogProductDetailDto;
import com.decor.product.dto.catalog.CatalogProductDto;
import com.decor.product.dto.catalog.CategoryDto;
import com.decor.product.services.CatalogService;
import com.decor.product.services.CatalogUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Catalogo publico: sin login (ver SecurityConfig y PermittedUrls del gateway)
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CatalogUsage catalogUsage;

    // sort: name (por defecto) | price_asc | price_desc | best_sellers. size: 24 por defecto, maximo 100
    @GetMapping("/products")
    public ResponseEntity<CatalogPageDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        CatalogPageDto result = catalogService.search(q, category, sort, page, size);
        // Visita: la primera pagina sin busqueda (las siguientes y cada letra de una busqueda no son visitas nuevas)
        if ((page == null || page == 0) && (q == null || q.isBlank())) {
            catalogUsage.visit();
        }
        return ResponseEntity.ok(result);
    }

    // Destacados para la home
    @GetMapping("/featured")
    public ResponseEntity<List<CatalogProductDto>> featured() {
        return ResponseEntity.ok(catalogService.featured());
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<CatalogProductDetailDto> getBySlug(@PathVariable String slug) throws ApiException {
        // Si no existe, getBySlug responde 404 antes de contar la vista
        CatalogProductDetailDto product = catalogService.getBySlug(slug);
        catalogUsage.productView(slug);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/bundles")
    public ResponseEntity<List<CatalogBundleDto>> bundles() {
        return ResponseEntity.ok(catalogService.bundles());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> categories() {
        return ResponseEntity.ok(catalogService.categories());
    }
}
