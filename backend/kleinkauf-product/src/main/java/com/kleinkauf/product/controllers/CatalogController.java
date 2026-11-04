package com.kleinkauf.product.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.dto.catalog.CatalogBundleDto;
import com.kleinkauf.product.dto.catalog.CatalogPageDto;
import com.kleinkauf.product.dto.catalog.CatalogProductDetailDto;
import com.kleinkauf.product.dto.catalog.CatalogProductDto;
import com.kleinkauf.product.dto.catalog.CategoryDto;
import com.kleinkauf.product.services.CatalogService;
import com.kleinkauf.product.services.CatalogUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Public catalog: no login (see SecurityConfig and the gateway's PermittedUrls)
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CatalogUsage catalogUsage;

    // sort: name (default) | price_asc | price_desc | best_sellers. size: 24 by default, max 100
    @GetMapping("/products")
    public ResponseEntity<CatalogPageDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        CatalogPageDto result = catalogService.search(q, category, sort, page, size);
        // Visit: the first page without a search (the following ones and each letter of a search are not new visits)
        if ((page == null || page == 0) && (q == null || q.isBlank())) {
            catalogUsage.visit();
        }
        return ResponseEntity.ok(result);
    }

    // Featured products for the home page
    @GetMapping("/featured")
    public ResponseEntity<List<CatalogProductDto>> featured() {
        return ResponseEntity.ok(catalogService.featured());
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<CatalogProductDetailDto> getBySlug(@PathVariable String slug) throws ApiException {
        // If it does not exist, getBySlug responds 404 before counting the view
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
