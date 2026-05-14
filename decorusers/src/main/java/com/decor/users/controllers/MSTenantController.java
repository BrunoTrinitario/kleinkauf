package com.decor.users.controllers;

import com.decor.users.dtos.ComTenantDto;
import com.decor.users.dtos.FullUserDto;
import com.decor.users.dtos.TenantDto;
import com.decor.users.exceptions.ApiException;
import com.decor.users.services.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/internal/tenants")
public class MSTenantController {
    @Autowired
    TenantService tenantService;
    @GetMapping("/all")
    public ResponseEntity<List<TenantDto>> getUserById() throws ApiException {
        List<TenantDto> tenant = tenantService.getAllTenant();
        return new ResponseEntity<>(tenant, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/id")
    public ResponseEntity<TenantDto> getUserById(@RequestParam String name) throws ApiException {
        TenantDto tenant = tenantService.getTenantByName(name);
        return new ResponseEntity<>(tenant, org.springframework.http.HttpStatus.OK);
    }
}
