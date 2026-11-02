package com.decor.product.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.product.constants.ErrorMessages;
import com.decor.product.dto.platform.ProductUsageDto;
import com.decor.product.services.PlatformUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
Plataforma (sin tenant): solo SUPERADMIN (ver SecurityConfig). El gateway nunca agrega tenant en
/api/v1/platform/**; igual, si llegara con uno responde 404, como users.
 */
@RestController
@RequestMapping("/api/v1/platform/products")
public class PlatformController {

    @Autowired
    private PlatformUsageService platformUsageService;

    // Productos y combos por schema. sort: products (por defecto) o bundles
    @GetMapping("/usage")
    public ResponseEntity<List<ProductUsageDto>> usage(@RequestParam(required = false) String sort) throws ApiException {
        if (TenantContext.getCurrentTenant() != null) {
            throw new ApiException(ErrorMessages.PLATFORM_ONLY, HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(platformUsageService.usage(sort));
    }
}
