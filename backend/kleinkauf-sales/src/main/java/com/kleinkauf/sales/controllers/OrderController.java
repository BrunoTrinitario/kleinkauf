package com.kleinkauf.sales.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.dto.CancelDto;
import com.kleinkauf.sales.dto.CheckoutDto;
import com.kleinkauf.sales.dto.LookupDto;
import com.kleinkauf.sales.dto.NewOrderDto;
import com.kleinkauf.sales.dto.OrderCreatedDto;
import com.kleinkauf.sales.dto.OrderDto;
import com.kleinkauf.sales.dto.OrderSummaryDto;
import com.kleinkauf.sales.dto.PageDto;
import com.kleinkauf.sales.services.OrderService;
import com.kleinkauf.sales.services.PricesChangedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Checkout de la tienda: publico (invitado o logueado), salvo mis pedidos (ver SecurityConfig)
@RestController
@RequestMapping("/api/v1/sales")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/checkout")
    public ResponseEntity<CheckoutDto> checkout() {
        return ResponseEntity.ok(orderService.checkout());
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderCreatedDto> create(@RequestBody NewOrderDto dto) throws ApiException, PricesChangedException {
        return new ResponseEntity<>(orderService.create(dto), HttpStatus.CREATED);
    }

    @PostMapping("/orders/lookup")
    public ResponseEntity<OrderDto> lookup(@RequestBody LookupDto dto) throws ApiException {
        return ResponseEntity.ok(orderService.lookup(dto));
    }

    // multipart: file (JPG, PNG o PDF hasta 5 MB) y email del pedido
    @PostMapping(value = "/orders/{number}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrderDto> uploadReceipt(@PathVariable String number,
                                                  @RequestParam String email,
                                                  @RequestParam MultipartFile file) throws ApiException {
        return ResponseEntity.ok(orderService.uploadReceipt(number, email, file));
    }

    @PostMapping("/orders/{number}/cancel")
    public ResponseEntity<OrderDto> cancel(@PathVariable String number, @RequestBody CancelDto dto) throws ApiException {
        return ResponseEntity.ok(orderService.cancel(number, dto));
    }

    @GetMapping("/orders/mine")
    public ResponseEntity<PageDto<OrderSummaryDto>> mine(@RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) throws ApiException {
        return ResponseEntity.ok(orderService.mine(page, size));
    }
}
