package com.decor.product.controllers;

import com.decor.product.dto.*;
import com.decor.product.dto.bundles.BundleDto;
import com.decor.product.dto.bundles.NewBundleDto;
import com.decor.product.dto.bundles.NewBundleProductDto;
import com.decor.product.exceptions.ApiException;
import com.decor.product.services.BundleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/bundles")
public class BundleController {
    @Autowired
    private BundleService bundleService;

    @GetMapping("/all")
    public ResponseEntity<ArrayList<BundleDto>> getAllBundles() throws ApiException {
        return ResponseEntity.ok(bundleService.getAllBundles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BundleDto> getBundleById(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(bundleService.getBundleById(id));
    }

    @PostMapping
    public ResponseEntity<BundleDto> createBundle(@RequestBody NewBundleDto dto) throws ApiException {
        return ResponseEntity.ok(bundleService.createBundle(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteBundle(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(bundleService.deleteBundleById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Boolean> changeBundleStatus(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(bundleService.changeBundleStatus(id));
    }

    @PutMapping("/{id}/product")
    public ResponseEntity<BundleDto> addOrUpdateProductFromBundle(@PathVariable Long id, @RequestBody NewBundleProductDto bundleProduct) throws ApiException {
        return ResponseEntity.ok(bundleService.addOrUpdateProductToBundle(id,bundleProduct));
    }

    @DeleteMapping("/{id}/product")
    public ResponseEntity<Boolean> removeProductFromBundle(@PathVariable Long id, @RequestBody NewBundleProductDto bundleProduct) throws ApiException {
        return ResponseEntity.ok(bundleService.removeProductFromBundle(id,bundleProduct));
    }
}
