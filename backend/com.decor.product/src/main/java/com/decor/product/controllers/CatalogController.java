package com.decor.product.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.dto.catalog.CatalogPageDto;
import com.decor.product.dto.catalog.CatalogProductDetailDto;
import com.decor.product.dto.catalog.CategoryDto;
import com.decor.product.services.CatalogService;
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

    // sort: name (por defecto) | price_asc | price_desc. size: 24 por defecto, maximo 100
    @GetMapping("/products")
    public ResponseEntity<CatalogPageDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        return ResponseEntity.ok(catalogService.search(q, category, sort, page, size));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<CatalogProductDetailDto> getBySlug(@PathVariable String slug) throws ApiException {
        return ResponseEntity.ok(catalogService.getBySlug(slug));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> categories() {
        return ResponseEntity.ok(catalogService.categories());
    }
}
