package com.kleinkauf.product.controllers;

import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.dto.bundles.BundleEnabledDto;
import com.kleinkauf.product.dto.bundles.NewBundleDto;
import com.kleinkauf.product.dto.bundles.NewBundleProductDto;
import com.kleinkauf.product.dto.bundles.UpdateBundlePriceDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.services.BundleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

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

    @PutMapping("/{id}/enabled")
    public ResponseEntity<BundleDto> setEnabled(@PathVariable Long id, @RequestBody BundleEnabledDto dto) throws ApiException {
        return ResponseEntity.ok(bundleService.setEnabled(id, dto.enabled()));
    }

    @PutMapping("/{id}/price")
    public ResponseEntity<BundleDto> updateBundlePrice(@PathVariable Long id, @RequestBody UpdateBundlePriceDto dto) throws ApiException {
        return ResponseEntity.ok(bundleService.updatePrice(id, dto));
    }

    @PutMapping("/{id}/product")
    public ResponseEntity<BundleDto> addOrUpdateProductFromBundle(@PathVariable Long id, @RequestBody NewBundleProductDto bundleProduct) throws ApiException {
        return ResponseEntity.ok(bundleService.addOrUpdateProductToBundle(id,bundleProduct));
    }

    @PutMapping("/{id}/product/bulk")
    public ResponseEntity<BundleDto> addOrUpdateProductFromBundle(@PathVariable Long id, @RequestBody List<NewBundleProductDto> bundleProduct) throws ApiException {
        return ResponseEntity.ok(bundleService.bulkUpdateBundle(id,bundleProduct));
    }

    @DeleteMapping("/{id}/product")
    public ResponseEntity<Boolean> removeProductFromBundle(@PathVariable Long id, @RequestBody NewBundleProductDto bundleProduct) throws ApiException {
        return ResponseEntity.ok(bundleService.removeProductFromBundle(id,bundleProduct));
    }
}
