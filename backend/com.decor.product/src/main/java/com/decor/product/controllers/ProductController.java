package com.decor.product.controllers;

import com.decor.product.dto.*;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Listado paginado del panel (stock exacto): q busca en nombre, descripcion y categoria.
    // size: 20 por defecto, maximo 100. Pide algun permiso del panel (ver SecurityConfig)
    @GetMapping
    public ResponseEntity<ProductPageDto> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        return ResponseEntity.ok(productService.searchProducts(q, category, page, size));
    }

    // Destacados en el orden de la home, con stock exacto: lo usa el panel para ordenarlos (el publico usa
    // /api/v1/catalog/featured). Pide algun area del panel (ver SecurityConfig)
    @GetMapping("/featured")
    public ResponseEntity<ArrayList<LightProductDto>> getFeaturedProducts() throws ApiException {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    // Solo administrador (PUT /api/v1/products/** en SecurityConfig)
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
        return ResponseEntity.ok(productService.createProduct(dto));
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

    // Precio, costo y margen: solo administrador (ver SecurityConfig)
    @GetMapping("/{id}/pricing")
    public ResponseEntity<PricingDto> getPricing(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.getPricing(id));
    }

    @PutMapping("/{id}/pricing")
    public ResponseEntity<PricingDto> updatePricing(@PathVariable Long id, @RequestBody UpdatePricingDto dto) throws ApiException {
        return ResponseEntity.ok(productService.updatePricing(id, dto));
    }
}
