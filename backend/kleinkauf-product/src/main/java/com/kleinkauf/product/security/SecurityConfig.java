package com.kleinkauf.product.security;

import com.kleinkauf.common.security.PermissionArea;
import com.kleinkauf.common.servlet.tenant.TenantFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PANEL_AREAS = {
            PermissionArea.PRODUCTOS.authority(), PermissionArea.PRECIOS.authority(),
            PermissionArea.STOCK.authority(), PermissionArea.COMBOS.authority()};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TenantFilter tenantFilter,HeaderAuthFilter headerAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Calls from other microservices (stock reservations from sales)
                        .requestMatchers("/api/internal/**").hasRole("INTERNAL")
                        // Platform dashboard (no tenant): per-tenant usage
                        .requestMatchers("/api/v1/platform/**").hasRole("SUPERADMIN")
                        // Public catalog
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        // Store dashboard: administrator only (the whole dashboard, not by areas). Before the
                        // /api/v1/products/** rules, which leave GETs open
                        .requestMatchers("/api/v1/products/dashboard").hasRole("ADMINISTRADOR")
                        // Store settings (name, theme, icon): administrator only
                        .requestMatchers("/api/v1/store/**").hasRole("ADMINISTRADOR")
                        // Permissions by area (PermissionArea). ADMINISTRADOR has all of them;
                        // EMPLEADO's and CLIENTE's are configured by the administrator in users
                        .requestMatchers("/api/v1/products/*/stock/movements", "/api/v1/products/stock/low")
                            .hasAuthority(PermissionArea.STOCK.authority())
                        // See cost and margin and edit price and cost
                        .requestMatchers("/api/v1/products/*/pricing").hasAuthority(PermissionArea.PRECIOS.authority())
                        // CSV: the file carries price and cost
                        .requestMatchers("/api/v1/products/export", "/api/v1/products/import/template", "/api/v1/products/import")
                            .access(AuthorizationManagers.allOf(
                                    AuthorityAuthorizationManager.hasAuthority(PermissionArea.PRODUCTOS.authority()),
                                    AuthorityAuthorizationManager.hasAuthority(PermissionArea.PRECIOS.authority())))
                        // Dashboard reads with exact stock: any dashboard area (EMPLEADO by default only
                        // has STOCK). Includes bundles, which say how many can be put together. The public uses /api/v1/catalog/**
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/featured",
                                "/api/v1/bundles/**")
                            .hasAnyAuthority(PANEL_AREAS)
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/api/v1/products/\\d+$"))
                            .hasAnyAuthority(PANEL_AREAS)
                        // Products: create, edit, delete, images, metadata and featured
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        // Bundles, including their price
                        .requestMatchers(HttpMethod.POST, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        // A product's images and metadata: they do not show stock, they are public
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/images", "/api/v1/products/*/metadata").permitAll()
                        // Whatever has no rule above (another method, a new endpoint) is not left open by default
                        .requestMatchers("/api/v1/products/**", "/api/v1/bundles/**").denyAll()
                        .requestMatchers("/products/images/**").permitAll()
                        // Share link (Open Graph): opened by a crawler, without login
                        .requestMatchers(HttpMethod.GET, "/share/products/*").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        tenantFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}