package com.kleinkauf.gateway;

import com.kleinkauf.gateway.constants.PermittedUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Routes the gateway lets through without a JWT
class PermittedUrlsTest {

    @Test
    void publicCatalog() {
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/featured"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/products/5/metadata"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/products"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/products/mesa-de-roble"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/categories"));
    }

    // They show the exact stock (and for bundles how many can be put together): they belong to the dashboard, the public uses the catalog
    @Test
    void readsWithExactStock_requireLogin() {
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/featured"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/5"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/search"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/bundles/all"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/bundles/3"));
    }

    @Test
    void platform_signUpAndLoginPublic_dashboardRequiresLogin() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/tenants"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/auth/login"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/tenants"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/admin/tenants"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/platform/admin/tenants"));

        assertTrue(PermittedUrls.isPlatformPath("/api/v1/platform/admin/tenants"));
        assertFalse(PermittedUrls.isPlatformPath("/api/v1/products/all"));
    }

    // They show the exact stock: they belong to the dashboard, not the catalog
    // Renewing the session: public, the 1-hour token may have expired
    @Test
    void refresh_isPublic() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/auth/refresh"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/auth/refresh"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/auth/refresh"));
    }

    // Logout: public, it only carries the refresh token
    @Test
    void logout_isPublic() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/auth/logout"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/platform/auth/logout"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/auth/logout"));
    }

    // Reporting a bug: public and with optional login (with a session it records who sent it)
    @Test
    void reportBug_publicWithOptionalLogin() {
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/bug-reports"));
        assertTrue(PermittedUrls.isOptionalAuth("POST", "/api/v1/bug-reports"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/bug-reports"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/admin/bug-reports"));
    }

    @Test
    void wsWithoutJwt_observabilityTicketRequiresLogin() {
        assertTrue(PermittedUrls.isPermitted("GET", "/ws"));
        assertFalse(PermittedUrls.isPermitted("POST", "/ws"));
        assertFalse(PermittedUrls.isPermitted("GET", "/ws/otra"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/platform/observability/ws-ticket"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/platform/observability/overview"));
    }

    // Share link: opened by a crawler, without login or Origin (the store goes in the path)
    @Test
    void shareLink() {
        assertTrue(PermittedUrls.isPermitted("GET", "/share/mi-tienda/products/mesa-de-roble"));
        assertFalse(PermittedUrls.isPermitted("POST", "/share/mi-tienda/products/mesa-de-roble"));
        assertFalse(PermittedUrls.isPermitted("GET", "/share/mi-tienda/products/mesa/otra"));

        assertTrue(PermittedUrls.SHARE_PATH.matcher("/share/mi-tienda/products/mesa-de-roble").matches());
        assertFalse(PermittedUrls.SHARE_PATH.matcher("/share/Mi_Tienda/products/mesa").matches());
        assertFalse(PermittedUrls.SHARE_PATH.matcher("/share/a/products/mesa").matches());
    }

    @Test
    void dashboardListingsRequireLogin() {
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/all"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/users"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/users/all"));
    }

    @Test
    void userAvatars_publicToRead_uploadAndDeleteRequireLogin() {
        assertTrue(PermittedUrls.isPermitted("GET", "/users/avatars/abc_123.png"));
        assertFalse(PermittedUrls.isPermitted("POST", "/users/avatars/abc_123.png"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/auth/me/avatar"));
        assertFalse(PermittedUrls.isPermitted("DELETE", "/api/v1/auth/me/avatar"));
    }

    @Test
    void storeSettings_publicReadsConfig_editingRequiresLogin() {
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/store"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/store/settings"));
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/store/settings"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/store/settings/icon"));
        assertFalse(PermittedUrls.isPermitted("DELETE", "/api/v1/store/settings/icon"));
    }

    @Test
    void checkout_public_myOrdersAndDashboardRequireLogin() {
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/catalog/bundles"));
        assertTrue(PermittedUrls.isPermitted("GET", "/api/v1/sales/checkout"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/lookup"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/sales/orders/mine"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/sales/admin/orders"));
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/sales/admin/settings"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/K7QM-4X2P/receipt"));
        assertTrue(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/K7QM-4X2P/cancel"));
        // {code} is a single segment: it does not open other routes that end the same way
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/sales/admin/orders/K7QM-4X2P/cancel"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/sales/orders/a/b/cancel"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/sales/admin/orders"));
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/sales/admin/orders/K7QM-4X2P/status"));

        // Only creating the order takes the user if logged in
        assertTrue(PermittedUrls.isOptionalAuth("POST", "/api/v1/sales/orders"));
        assertFalse(PermittedUrls.isOptionalAuth("POST", "/api/v1/sales/orders/lookup"));
        assertFalse(PermittedUrls.isOptionalAuth("GET", "/api/v1/sales/checkout"));
    }

    @Test
    void markFeaturedRequiresLogin() {
        assertFalse(PermittedUrls.isPermitted("PUT", "/api/v1/products/5/featured"));
    }

    @Test
    void costAndStockRequireLogin() {
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/5/pricing"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/stock/low"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/5/stock/movements"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/products"));
        // The CSV includes the cost
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/export"));
        assertFalse(PermittedUrls.isPermitted("GET", "/api/v1/products/import/template"));
        assertFalse(PermittedUrls.isPermitted("POST", "/api/v1/products/import"));
    }
}
