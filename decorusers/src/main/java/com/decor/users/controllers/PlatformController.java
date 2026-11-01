package com.decor.users.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.dtos.LoggedUser;
import com.decor.users.dtos.LoginUserDto;
import com.decor.users.dtos.platform.NewTenantDto;
import com.decor.users.dtos.platform.PlatformTenantDto;
import com.decor.users.dtos.platform.TenantStatusDto;
import com.decor.users.services.PlatformService;
import com.decor.users.services.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
Plataforma (sin tenant):
- POST /tenants y POST /auth/login: publicos (alta por autoservicio y login del superadmin).
- /admin/**: solo SUPERADMIN (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TenantService tenantService;

    @PostMapping("/tenants")
    public ResponseEntity<PlatformTenantDto> signUp(@RequestBody NewTenantDto dto) throws ApiException {
        platformService.requirePlatformContext();
        return new ResponseEntity<>(tenantService.createTenant(dto), HttpStatus.CREATED);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoggedUser> login(@RequestBody LoginUserDto dto) throws ApiException {
        return ResponseEntity.ok(platformService.login(dto.email(), dto.password()));
    }

    @GetMapping("/admin/tenants")
    public ResponseEntity<List<PlatformTenantDto>> listTenants() throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(tenantService.listTenants());
    }

    @GetMapping("/admin/tenants/{id}")
    public ResponseEntity<PlatformTenantDto> getTenant(@PathVariable Long id) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(tenantService.getTenant(id));
    }

    @PostMapping("/admin/tenants")
    public ResponseEntity<PlatformTenantDto> createTenant(@RequestBody NewTenantDto dto) throws ApiException {
        platformService.requirePlatformContext();
        return new ResponseEntity<>(tenantService.createTenant(dto), HttpStatus.CREATED);
    }

    @PutMapping("/admin/tenants/{id}/enabled")
    public ResponseEntity<PlatformTenantDto> setEnabled(@PathVariable Long id, @RequestBody TenantStatusDto dto) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(tenantService.setEnabled(id, dto.enabled()));
    }

    @DeleteMapping("/admin/tenants/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) throws ApiException {
        platformService.requirePlatformContext();
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}
