package com.decor.sales.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.dto.AdminOrderDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PageDto;
import com.decor.sales.dto.SalesSettingsDto;
import com.decor.sales.services.AdminOrderService;
import com.decor.sales.services.SalesSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Pedidos y configuracion de ventas: solo ADMINISTRADOR (ver SecurityConfig)
@RestController
@RequestMapping("/api/v1/sales/admin")
public class AdminSalesController {

    @Autowired
    private AdminOrderService adminOrderService;

    @Autowired
    private SalesSettingsService settingsService;

    // from / to: yyyy-MM-dd. q: numero de pedido o parte del email
    @GetMapping("/orders")
    public ResponseEntity<PageDto<OrderSummaryDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Boolean stockReview,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        AdminOrderService.Filter filter = new AdminOrderService.Filter(status, channel, paymentMethod, stockReview, from, to, q);
        return ResponseEntity.ok(adminOrderService.list(filter, page, size));
    }

    @GetMapping("/orders/{number}")
    public ResponseEntity<AdminOrderDto> get(@PathVariable String number) throws ApiException {
        return ResponseEntity.ok(adminOrderService.get(number));
    }

    @GetMapping("/settings")
    public ResponseEntity<SalesSettingsDto> getSettings() {
        return ResponseEntity.ok(settingsService.get());
    }

    @PutMapping("/settings")
    public ResponseEntity<SalesSettingsDto> updateSettings(@RequestBody SalesSettingsDto dto) throws ApiException {
        return ResponseEntity.ok(settingsService.update(dto));
    }
}
