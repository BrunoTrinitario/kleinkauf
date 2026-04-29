package com.decor.product.controllers;

import com.decor.product.dto.*;
import com.decor.product.exceptions.ApiException;
import com.decor.product.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<ArrayList<LightProductDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FullProductDto> getProductById(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<FullProductDto> getProductByName(@RequestParam String name) throws ApiException {
        return ResponseEntity.ok(productService.getProductByName(name));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteProduct(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(productService.deleteProductById(id));
    }

    @PutMapping
    public ResponseEntity<FullProductDto> updateProduct(@RequestBody LightProductDto dto) throws ApiException {
        return ResponseEntity.ok(productService.updateProduct(dto));
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
}
