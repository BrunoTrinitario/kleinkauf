package com.decor.product.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.dto.stock.LowStockDto;
import com.decor.product.dto.stock.NewStockMovementDto;
import com.decor.product.dto.stock.StockMovementDto;
import com.decor.product.services.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Movimientos de stock y alerta de stock bajo: administrador y empleado (ver SecurityConfig)
@RestController
@RequestMapping("/api/v1/products")
public class StockController {

    @Autowired
    private StockService stockService;

    @PostMapping("/{id}/stock/movements")
    public ResponseEntity<StockMovementDto> registerMovement(@PathVariable Long id, @RequestBody NewStockMovementDto dto) throws ApiException {
        return new ResponseEntity<>(stockService.registerMovement(id, dto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/stock/movements")
    public ResponseEntity<List<StockMovementDto>> getMovements(@PathVariable Long id) throws ApiException {
        return ResponseEntity.ok(stockService.getMovements(id));
    }

    @GetMapping("/stock/low")
    public ResponseEntity<List<LowStockDto>> getLowStock() {
        return ResponseEntity.ok(stockService.getLowStock());
    }
}
