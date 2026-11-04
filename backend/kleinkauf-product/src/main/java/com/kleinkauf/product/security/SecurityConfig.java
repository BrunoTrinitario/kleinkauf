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
                        // Llamadas de otros microservicios (reservas de stock de sales)
                        .requestMatchers("/api/internal/**").hasRole("INTERNAL")
                        // Panel de la plataforma (sin tenant): uso por tenant
                        .requestMatchers("/api/v1/platform/**").hasRole("SUPERADMIN")
                        // Catalogo publico
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        // Panel de la tienda: solo el administrador (todo el panel, no por areas). Antes de las
                        // reglas de /api/v1/products/**, que dejan los GET abiertos
                        .requestMatchers("/api/v1/products/dashboard").hasRole("ADMINISTRADOR")
                        // Permisos por area (PermissionArea). El ADMINISTRADOR tiene todas;
                        // los de EMPLEADO y CLIENTE los configura el administrador en users
                        .requestMatchers("/api/v1/products/*/stock/movements", "/api/v1/products/stock/low")
                            .hasAuthority(PermissionArea.STOCK.authority())
                        // Ver costo y margen y editar precio y costo
                        .requestMatchers("/api/v1/products/*/pricing").hasAuthority(PermissionArea.PRECIOS.authority())
                        // CSV: el archivo trae precio y costo
                        .requestMatchers("/api/v1/products/export", "/api/v1/products/import/template", "/api/v1/products/import")
                            .access(AuthorizationManagers.allOf(
                                    AuthorityAuthorizationManager.hasAuthority(PermissionArea.PRODUCTOS.authority()),
                                    AuthorityAuthorizationManager.hasAuthority(PermissionArea.PRECIOS.authority())))
                        // Lecturas del panel con stock exacto: cualquier area del panel (el EMPLEADO por defecto solo
                        // tiene STOCK). Incluye los combos, que dicen cuantos se pueden armar. El publico usa /api/v1/catalog/**
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/featured",
                                "/api/v1/bundles/**")
                            .hasAnyAuthority(PANEL_AREAS)
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/api/v1/products/\\d+$"))
                            .hasAnyAuthority(PANEL_AREAS)
                        // Productos: alta, edicion, borrado, imagenes, metadatos y destacados
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        // Combos, incluido su precio
                        .requestMatchers(HttpMethod.POST, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        // Imagenes y metadatos de un producto: no muestran stock, son publicos
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/images", "/api/v1/products/*/metadata").permitAll()
                        // Lo que no tiene regla arriba (otro metodo, un endpoint nuevo) no queda abierto por defecto
                        .requestMatchers("/api/v1/products/**", "/api/v1/bundles/**").denyAll()
                        .requestMatchers("/products/images/**").permitAll()
                        // Link para compartir (Open Graph): lo abre un crawler, sin login
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