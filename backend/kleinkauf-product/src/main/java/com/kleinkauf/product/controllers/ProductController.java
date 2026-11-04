package com.kleinkauf.product.controllers;

import com.kleinkauf.product.dto.*;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    @Autowired
    private ProductService productService;

    // Paginated dashboard listing (exact stock): q searches name, description and category.
    // size: 20 by default, max 100. Requires some dashboard permission (see SecurityConfig)
    @GetMapping
    public ResponseEntity<ProductPageDto> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        return ResponseEntity.ok(productService.searchProducts(q, category, page, size));
    }

    // Featured products in home page order, with exact stock: the dashboard uses it to sort them (the public uses
    // /api/v1/catalog/featured). Requires some dashboard area (see SecurityConfig)
    @GetMapping("/featured")
    public ResponseEntity<ArrayList<LightProductDto>> getFeaturedProducts() throws ApiException {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    // Administrator only (PUT /api/v1/products/** in SecurityConfig)
    @PutMapping("/{id}/featured")
    public ResponseEntity<FullProductDto> setFeatured(@PathVariable Long id, @RequestBody FeaturedDto dto) throws ApiException {
        return ResponseEntity.ok(productService.setFeatured(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FullProductDto> getProductById(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteProduct(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.deleteProductById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FullProductDto> updateProduct(@PathVariable Long id, @RequestBody BasicUpdateDto dto) throws ApiException {
        return ResponseEntity.ok(productService.updateProduct(id, dto));
    }
    @PostMapping
    public ResponseEntity<FullProductDto> createProduct(@RequestBody NewProductDto dto) throws ApiException {
        return new ResponseEntity<>(productService.createProduct(dto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/images")
    public ResponseEntity<ArrayList<ImageDto>> getImages(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.getProductImages(id));
    }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    public ResponseEntity<Boolean> addImages(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files
    ) throws ApiException {
        return ResponseEntity.ok(productService.saveImages(id,files));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Boolean> deleteImage(@PathVariable Long imageId) throws ApiException {
        return ResponseEntity.ok(productService.deleteImageById(imageId));
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<MetaDataDto> getMetadata(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.getProductMetaData(id));
    }

    @PutMapping("/{id}/metadata")
    public ResponseEntity<FullProductDto> updateMetadata(
            @PathVariable Long id,
            @RequestBody MetaDataDto dto
    ) throws ApiException {
        return ResponseEntity.ok(productService.updateMetadata(id, dto));
    }

    // Price, cost and margin: administrator only (see SecurityConfig)
    @GetMapping("/{id}/pricing")
    public ResponseEntity<PricingDto> getPricing(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.getPricing(id));
    }

    @PutMapping("/{id}/pricing")
    public ResponseEntity<PricingDto> updatePricing(@PathVariable Long id, @RequestBody UpdatePricingDto dto) throws ApiException {
        return ResponseEntity.ok(productService.updatePricing(id, dto));
    }
}
