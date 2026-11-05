package com.kleinkauf.product.controllers;

import com.kleinkauf.product.dto.csv.ImportResultDto;
import com.kleinkauf.product.services.ProductCsvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Import and export products as CSV: administrator only (see SecurityConfig)
@RestController
@RequestMapping("/api/v1/products")
public class ProductCsvController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    @Autowired
    private ProductCsvService productCsvService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportProducts() {
        return csv(productCsvService.exportCsv(), "productos.csv");
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return csv(productCsvService.template(), "plantilla-productos.csv");
    }

    // 200 with what was created and updated; 400 with the errors per row (nothing is imported)
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importProducts(@RequestParam("file") MultipartFile file) {
        ImportResultDto result = productCsvService.importCsv(file);
        return result.hasErrors()
                ? ResponseEntity.badRequest().body(result)
                : ResponseEntity.ok(result);
    }

    private static ResponseEntity<byte[]> csv(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
