package com.kleinkauf.product.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.dto.store.StoreDto;
import com.kleinkauf.product.dto.store.UpdateStoreDto;
import com.kleinkauf.product.services.StoreSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Store settings: ADMINISTRADOR only (see SecurityConfig). The public reads them at GET /api/v1/catalog/store
@RestController
@RequestMapping("/api/v1/store/settings")
public class StoreSettingsController {

    @Autowired
    private StoreSettingsService storeSettingsService;

    @GetMapping
    public ResponseEntity<StoreDto> get() {
        return ResponseEntity.ok(storeSettingsService.get());
    }

    @PutMapping
    public ResponseEntity<StoreDto> update(@RequestBody UpdateStoreDto dto) throws ApiException {
        return ResponseEntity.ok(storeSettingsService.update(dto));
    }

    @PostMapping(value = "/icon", consumes = "multipart/form-data")
    public ResponseEntity<StoreDto> uploadIcon(@RequestParam("file") MultipartFile file) throws ApiException {
        return ResponseEntity.ok(storeSettingsService.saveIcon(file));
    }

    @DeleteMapping("/icon")
    public ResponseEntity<StoreDto> deleteIcon() {
        return ResponseEntity.ok(storeSettingsService.deleteIcon());
    }
}
