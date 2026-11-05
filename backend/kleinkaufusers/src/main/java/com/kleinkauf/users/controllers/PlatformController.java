package com.kleinkauf.users.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.security.CustomUserDetails;
import com.kleinkauf.users.dtos.LoggedUser;
import com.kleinkauf.users.dtos.LoginUserDto;
import com.kleinkauf.users.dtos.RefreshTokenDto;
import com.kleinkauf.users.dtos.platform.BugReportDto;
import com.kleinkauf.users.dtos.platform.BugReportPageDto;
import com.kleinkauf.users.dtos.platform.BugReportStatusDto;
import com.kleinkauf.users.dtos.platform.NewSuperadminDto;
import com.kleinkauf.users.dtos.platform.NewTenantDto;
import com.kleinkauf.users.dtos.platform.PlatformDashboardDto;
import com.kleinkauf.users.dtos.platform.PlatformTenantDto;
import com.kleinkauf.users.dtos.platform.SuperadminDto;
import com.kleinkauf.users.dtos.platform.SuperadminStatusDto;
import com.kleinkauf.users.dtos.platform.TenantStatusDto;
import com.kleinkauf.users.dtos.platform.TenantUsageDto;
import com.kleinkauf.users.services.BugReportService;
import com.kleinkauf.users.services.PlatformDashboardService;
import com.kleinkauf.users.services.PlatformService;
import com.kleinkauf.users.services.SuperadminService;
import com.kleinkauf.users.services.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
Platform (no tenant):
- POST /tenants and POST /auth/login: public (self-service sign-up and superadmin login).
- /admin/**: SUPERADMIN only (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private PlatformDashboardService dashboardService;

    @Autowired
    private SuperadminService superadminService;

    @Autowired
    private BugReportService bugReportService;

    @PostMapping("/tenants")
    public ResponseEntity<PlatformTenantDto> signUp(@RequestBody NewTenantDto dto) throws ApiException {
        platformService.requirePlatformContext();
        return new ResponseEntity<>(tenantService.createTenant(dto), HttpStatus.CREATED);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoggedUser> login(@RequestBody LoginUserDto dto) throws ApiException {
        return ResponseEntity.ok(platformService.login(dto.email(), dto.password()));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<LoggedUser> refresh(@RequestBody RefreshTokenDto dto) throws ApiException {
        return ResponseEntity.ok(platformService.refresh(dto == null ? null : dto.refreshToken()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenDto dto) throws ApiException {
        platformService.logout(dto == null ? null : dto.refreshToken());
        return ResponseEntity.noContent().build();
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

    // Tenant summary and sign-ups per period. groupBy: DAY (default), WEEK or MONTH
    @GetMapping("/admin/dashboard")
    public ResponseEntity<PlatformDashboardDto> dashboard(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String groupBy
    ) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(dashboardService.dashboard(from, to, groupBy));
    }

    // Users, active users and last activity per tenant. sort: users (default), activeUsers, lastActivity, createdAt
    @GetMapping("/admin/usage")
    public ResponseEntity<List<TenantUsageDto>> usage(@RequestParam(required = false) String sort) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(dashboardService.usage(sort));
    }

    @GetMapping("/admin/superadmins")
    public ResponseEntity<List<SuperadminDto>> listSuperadmins() throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(superadminService.list());
    }

    @PostMapping("/admin/superadmins")
    public ResponseEntity<SuperadminDto> createSuperadmin(@RequestBody NewSuperadminDto dto) throws ApiException {
        platformService.requirePlatformContext();
        return new ResponseEntity<>(superadminService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/admin/superadmins/{id}/active")
    public ResponseEntity<SuperadminDto> setSuperadminActive(@PathVariable Long id,
                                                             @RequestBody SuperadminStatusDto dto,
                                                             @AuthenticationPrincipal CustomUserDetails currentUser) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(superadminService.setActive(id, dto.active(), currentUser.getId()));
    }

    // Bug reports from the stores. status: pending (default), resolved or all. size: 20 by default, max 100
    @GetMapping("/admin/bug-reports")
    public ResponseEntity<BugReportPageDto> listBugReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(bugReportService.list(status, page, size));
    }

    @PutMapping("/admin/bug-reports/{id}/resolved")
    public ResponseEntity<BugReportDto> setBugReportResolved(@PathVariable Long id,
                                                             @RequestBody BugReportStatusDto dto) throws ApiException {
        platformService.requirePlatformContext();
        return ResponseEntity.ok(bugReportService.setResolved(id, dto.resolved()));
    }
}
