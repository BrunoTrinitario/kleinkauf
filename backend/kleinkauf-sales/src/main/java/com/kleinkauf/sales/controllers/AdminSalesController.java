package com.kleinkauf.sales.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.dto.AdminOrderDto;
import com.kleinkauf.sales.dto.LocalOrderDto;
import com.kleinkauf.sales.dto.OrderSummaryDto;
import com.kleinkauf.sales.dto.PageDto;
import com.kleinkauf.sales.dto.SalesSettingsDto;
import com.kleinkauf.sales.dto.StatusUpdateDto;
import com.kleinkauf.sales.services.AdminOrderService;
import com.kleinkauf.sales.services.SalesSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    // Venta del local (ya cobrada)
    @PostMapping("/orders")
    public ResponseEntity<AdminOrderDto> createLocal(@RequestBody LocalOrderDto dto) throws ApiException {
        return new ResponseEntity<>(adminOrderService.createLocal(dto), HttpStatus.CREATED);
    }

    @GetMapping("/orders/{number}")
    public ResponseEntity<AdminOrderDto> get(@PathVariable String number) throws ApiException {
        return ResponseEntity.ok(adminOrderService.get(number));
    }

    // Validar, rechazar, avanzar o cancelar (ver OrderStatus.canChangeTo)
    @PutMapping("/orders/{number}/status")
    public ResponseEntity<AdminOrderDto> changeStatus(@PathVariable String number, @RequestBody StatusUpdateDto dto) throws ApiException {
        return ResponseEntity.ok(adminOrderService.changeStatus(number, dto));
    }

    @GetMapping("/orders/{number}/receipts/{id}")
    public ResponseEntity<Resource> receipt(@PathVariable String number, @PathVariable Long id) throws ApiException {
        AdminOrderService.ReceiptFile receipt = adminOrderService.receipt(number, id);
        String extension = receipt.receipt().getPath().substring(receipt.receipt().getPath().lastIndexOf('.'));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.receipt().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("comprobante-" + number.trim().toUpperCase() + "-" + id + extension)
                        .build().toString())
                .body(receipt.file());
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
