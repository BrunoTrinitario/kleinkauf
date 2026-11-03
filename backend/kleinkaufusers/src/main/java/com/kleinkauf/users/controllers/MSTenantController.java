package com.kleinkauf.users.controllers;

import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.services.TenantService;
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

    // El gateway busca por url y products por schema
    @GetMapping("/id")
    public ResponseEntity<TenantDto> getTenant(@RequestParam(required = false) String url,
                                               @RequestParam(required = false) String schema) throws ApiException {
        TenantDto tenant;
        if (schema != null) {
            tenant = tenantService.getTenantBySchema(schema);
        } else if (url != null) {
            tenant = tenantService.getTenantByUrl(url);
        } else {
            throw new ApiException("Falta el parametro url o schema", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(tenant, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/cors")
    public ResponseEntity<Boolean> getUserByUrl(@RequestParam String url) throws ApiException {
        Boolean exists = tenantService.existsTenantByUrl(url);
        return new ResponseEntity<>(exists, org.springframework.http.HttpStatus.OK);
    }
}
