package com.kleinkauf.sales.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.dto.reports.ProductReportDto;
import com.kleinkauf.sales.dto.reports.SalesSummaryDto;
import com.kleinkauf.sales.services.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
Sales reports: ADMINISTRADOR only (/api/v1/sales/admin/** in SecurityConfig).
from / to: yyyy-MM-dd, store days, inclusive (by default the last 30 days, max 366).
 */
@RestController
@RequestMapping("/api/v1/sales/admin/reports")
public class AdminReportController {

    @Autowired
    private ReportService reportService;

    // groupBy: day (default) | week | month. channel: ONLINE | LOCAL. paymentMethod: EFECTIVO, TRANSFERENCIA...
    @GetMapping("/summary")
    public ResponseEntity<SalesSummaryDto> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String paymentMethod
    ) throws ApiException {
        return ResponseEntity.ok(reportService.summary(from, to, groupBy, channel, paymentMethod));
    }

    // sort: units (default) | revenue | profit. limit: 20 by default, max 100
    @GetMapping("/products")
    public ResponseEntity<ProductReportDto> products(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer limit
    ) throws ApiException {
        return ResponseEntity.ok(reportService.products(from, to, channel, sort, limit));
    }
}
