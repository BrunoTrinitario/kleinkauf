package com.kleinkauf.product.controllers;

import com.kleinkauf.common.dto.stock.ReservationDto;
import com.kleinkauf.common.dto.stock.ReservationRequestDto;
import com.kleinkauf.common.dto.stock.SoldItemsDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.services.StockReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Reservas de stock de los pedidos: solo otros microservicios con token interno (ver HeaderAuthFilter)
@RestController
@RequestMapping("/api/internal/products/reservations")
public class InternalStockController {

    @Autowired
    private StockReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationDto> reserve(@RequestBody ReservationRequestDto request) throws ApiException {
        return ResponseEntity.ok(reservationService.reserve(request));
    }

    @PostMapping("/{reference}/release")
    public ResponseEntity<Void> release(@PathVariable String reference) throws ApiException {
        reservationService.release(reference);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reference}/sold")
    public ResponseEntity<Void> markSold(@PathVariable String reference, @RequestBody SoldItemsDto dto) throws ApiException {
        reservationService.markSold(reference, dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reference}/unsold")
    public ResponseEntity<Void> unmarkSold(@PathVariable String reference, @RequestBody SoldItemsDto dto) throws ApiException {
        reservationService.unmarkSold(reference, dto);
        return ResponseEntity.noContent().build();
    }
}
