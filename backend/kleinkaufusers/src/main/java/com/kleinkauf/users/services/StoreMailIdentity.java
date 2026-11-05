package com.kleinkauf.users.services;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.repositories.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/*
Who an email "comes from" for the current request's store (docs/mail.md):
- name: the store name set in its settings (products); if products does not answer, the one derived from the
  identifier ("mi_tienda" -> "Mi tienda"). Without a store (platform), mail.from.platform-name.
- replyTo: the email of the store owner (its first ADMINISTRADOR), so the buyer's reply reaches the store.
- url: the store's frontend (links in the emails); without a store, the platform's.
The address is always ours (mail.from.address): sending as the store's own address would fail DMARC.
 */
@Component
public class StoreMailIdentity {

    public record Sender(String name, String replyTo, String url) {
    }

    @Autowired
    private ProductsClient productsClient;

    @Autowired
    private RoleRepository roleRepository;

    // The tenants table is in public: JPA only sees the schema of the request's store
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${mail.from.platform-name:KleinKauf}")
    private String platformName;

    @Value("${platform.scheme:http}")
    private String platformScheme;

    @Value("${platform.domain:localhost:2999}")
    private String platformDomain;

    public Sender current() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank() || TenantConstants.PUBLIC.equals(tenant)) {
            return new Sender(platformName, null, platformUrl());
        }
        String name = productsClient.getStoreName(tenant);
        List<String> admins = roleRepository.findActiveEmailsWithType(RolesType.ADMINISTRADOR.name());
        return new Sender(name != null ? name : defaultName(tenant), admins.isEmpty() ? null : admins.get(0), url(tenant));
    }

    // The store's frontend URL; without a store (or if it was deleted), the platform's
    public String url(String tenant) {
        List<String> urls = jdbcTemplate.queryForList(
                "SELECT url FROM public.tenant WHERE schema = ? AND deleted_at IS NULL", String.class, tenant);
        return urls.isEmpty() || urls.get(0) == null ? platformUrl() : urls.get(0);
    }

    private String platformUrl() {
        return platformScheme + "://" + platformDomain;
    }

    // Same rule as products (StoreSettingsService.defaultName): "mi_tienda" -> "Mi tienda"
    static String defaultName(String schema) {
        String spaced = schema.replace('_', ' ').trim();
        return spaced.isEmpty() ? "Tienda" : spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }
}
