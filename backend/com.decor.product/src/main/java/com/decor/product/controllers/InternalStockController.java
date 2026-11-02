package com.decor.product.controllers;

import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationRequestDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.services.StockReservationService;
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
}
