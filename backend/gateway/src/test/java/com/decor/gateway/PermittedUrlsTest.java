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
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/products"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/products/mesa-de-roble"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/categories"));
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
    void checkout_publico_misPedidosYPanelPidenLogin() {
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/bundles"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/sales/checkout"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/lookup"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/sales/orders/mine"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/sales/admin/orders"));
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/sales/admin/settings"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/K7QM-4X2P/receipt"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/K7QM-4X2P/cancel"));
        // {code} es un solo segmento: no abre otras rutas que terminen igual
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/sales/admin/orders/K7QM-4X2P/cancel"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/a/b/cancel"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/sales/admin/orders"));
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/sales/admin/orders/K7QM-4X2P/status"));

        // Solo crear el pedido toma el usuario si viene logueado
        assertTrue(PermittedUrls.isOptionalAuth("POST", "/api/v1/sales/orders"));
        assertFalse(PermittedUrls.isOptionalAuth("POST", "/api/v1/sales/orders/lookup"));
        assertFalse(PermittedUrls.isOptionalAuth("GET", "/api/v1/sales/checkout"));
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
