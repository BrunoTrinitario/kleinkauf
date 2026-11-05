package com.kleinkauf.product.controllers;

import com.kleinkauf.product.dto.dashboard.ProductDashboardDto;
import com.kleinkauf.product.services.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Store dashboard: ADMINISTRADOR only, not by areas (see SecurityConfig and docs/panel-admin.md)
@RestController
@RequestMapping("/api/v1/products")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ProductDashboardDto> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }
}
