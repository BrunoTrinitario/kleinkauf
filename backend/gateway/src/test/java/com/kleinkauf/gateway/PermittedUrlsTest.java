package com.kleinkauf.gateway;

import com.kleinkauf.gateway.constants.PermittedUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Rutas que el gateway deja pasar sin JWT
class PermittedUrlsTest {

    @Test
    void catalogoPublico() {
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/featured"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/products/5/metadata"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/products"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/products/mesa-de-roble"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/categories"));
    }

    // Muestran el stock exacto (y los combos cuantos se pueden armar): son del panel, el publico usa el catalogo
    @Test
    void lecturasConStockExacto_pidenLogin() {
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/featured"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/5"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/search"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/bundles/all"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/bundles/3"));
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

    // Muestran el stock exacto: son del panel, no del catalogo
    // Renovar la sesion: publico, el token de 1 hora puede estar vencido
    @Test
    void refresh_esPublico() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/auth/refresh"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/auth/refresh"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/auth/refresh"));
    }

    // Cerrar sesion: publico, solo lleva el refresh
    @Test
    void logout_esPublico() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/auth/logout"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/auth/logout"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/auth/logout"));
    }

    // Reportar un bug: publico y con login opcional (con sesion queda quien lo mando)
    @Test
    void reportarUnBug_publicoConLoginOpcional() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/bug-reports"));
        assertTrue(PermittedUrls.isOptionalAuth("POST", "/api/v1/bug-reports"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/bug-reports"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/admin/bug-reports"));
    }

    @Test
    void wsSinJwt_ticketDeObservabilityPideLogin() {
        assertTrue(PermittedUrls.isPermitted("GET", "/ws"));
        assertFalse(PermittedUrls.isPermitted("POST", "/ws"));
        assertFalse(PermittedUrls.isPermitted("GET", "/ws/otra"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/platform/observability/ws-ticket"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/observability/overview"));
    }

    // Link para compartir: lo abre un crawler, sin login ni Origin (la tienda va en la ruta)
    @Test
    void linkParaCompartir() {
        assertTrue(PermittedUrls.isPermitted("GET", "/share/mi-tienda/products/mesa-de-roble"));
        assertFalse(PermittedUrls.isPermitted("POST", "/share/mi-tienda/products/mesa-de-roble"));
        assertFalse(PermittedUrls.isPermitted("GET", "/share/mi-tienda/products/mesa/otra"));

        assertTrue(PermittedUrls.SHARE_PATH.matcher("/share/mi-tienda/products/mesa-de-roble").matches());
        assertFalse(PermittedUrls.SHARE_PATH.matcher("/share/Mi_Tienda/products/mesa").matches());
        assertFalse(PermittedUrls.SHARE_PATH.matcher("/share/a/products/mesa").matches());
    }

    @Test
    void listadosDelPanelPidenLogin() {
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/all"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/users"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/users/all"));
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
