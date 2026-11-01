package com.decor.gateway;

import com.decor.gateway.constants.PermittedUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Rutas que el gateway deja pasar sin JWT
class PermittedUrlsTest {

    @Test
    void catalogoPublico() {
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/products/all"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/products/featured"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/products/5"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/bundles/all"));
    }

    @Test
    void plataforma_altaYLoginPublicos_panelPideLogin() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/tenants"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/auth/login"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/tenants"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/admin/tenants"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/platform/admin/tenants"));

        assertTrue(PermittedUrls.isPlatformPath("/api/v1/platform/admin/tenants"));
        assertFalse(PermittedUrls.isPlatformPath("/api/v1/products/all"));
    }

    @Test
    void marcarDestacadoPideLogin() {
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/products/5/featured"));
    }

    @Test
    void costoYStockPidenLogin() {
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/5/pricing"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/stock/low"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/5/stock/movements"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/products"));
        // El CSV incluye el costo
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/export"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/import/template"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/products/import"));
    }
}
