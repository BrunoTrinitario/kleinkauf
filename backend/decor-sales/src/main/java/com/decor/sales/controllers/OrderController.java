package com.decor.sales.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.dto.CheckoutDto;
import com.decor.sales.dto.LookupDto;
import com.decor.sales.dto.NewOrderDto;
import com.decor.sales.dto.OrderCreatedDto;
import com.decor.sales.dto.OrderDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PageDto;
import com.decor.sales.services.OrderService;
import com.decor.sales.services.PricesChangedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/orders/mine")
    public ResponseEntity<PageDto<OrderSummaryDto>> mine(@RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) throws ApiException {
        return ResponseEntity.ok(orderService.mine(page, size));
    }
}
