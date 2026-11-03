package com.decor.product.controllers;

import com.decor.product.dto.dashboard.ProductDashboardDto;
import com.decor.product.services.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Panel de la tienda: solo ADMINISTRADOR, no por areas (ver SecurityConfig y docs/panel-admin.md)
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
