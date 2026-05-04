package com.decor.users.controllers;

import com.decor.users.dtos.ComTenantDto;
import com.decor.users.dtos.FullUserDto;
import com.decor.users.exceptions.ApiException;
import com.decor.users.services.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/tenants")
public class MSTenantController {
    @Autowired
    TenantService tenantService;
    @GetMapping("/")
    public ResponseEntity<ComTenantDto> getUserById(@RequestParam String url) throws ApiException {
        ComTenantDto tenant = tenantService.getTenantByUrl(url);
        return new ResponseEntity<>(tenant, org.springframework.http.HttpStatus.OK);
    }
}
