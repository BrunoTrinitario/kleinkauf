package com.kleinkauf.product.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.platform.ProductUsageDto;
import com.kleinkauf.product.services.PlatformUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
Platform (no tenant): SUPERADMIN only (see SecurityConfig). The gateway never adds a tenant on
/api/v1/platform/**; still, if one arrived it responds 404, like users.
 */
@RestController
@RequestMapping("/api/v1/platform/products")
public class PlatformController {

    @Autowired
    private PlatformUsageService platformUsageService;

    // Products and bundles per schema. sort: products (default) or bundles
    @GetMapping("/usage")
    public ResponseEntity<List<ProductUsageDto>> usage(@RequestParam(required = false) String sort) throws ApiException {
        if (TenantContext.getCurrentTenant() != null) {
            throw new ApiException(ErrorMessages.PLATFORM_ONLY, HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(platformUsageService.usage(sort));
    }
}
