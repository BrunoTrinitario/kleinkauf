package com.decor.product.controllers;

import com.decor.product.dto.csv.ImportResultDto;
import com.decor.product.services.ProductCsvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Importar y exportar productos en CSV: solo administrador (ver SecurityConfig)
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

    // 200 con lo creado y actualizado; 400 con los errores por fila (no se importa nada)
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
