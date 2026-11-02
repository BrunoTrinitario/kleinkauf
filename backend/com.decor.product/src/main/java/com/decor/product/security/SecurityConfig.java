package com.decor.product.security;

import com.decor.common.security.PermissionArea;
import com.decor.common.servlet.tenant.TenantFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TenantFilter tenantFilter,HeaderAuthFilter headerAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Catalogo publico
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
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
                        // Listado del panel con stock exacto: cualquier area del panel (el EMPLEADO por defecto solo tiene STOCK)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/all").hasAnyAuthority(
                                PermissionArea.PRODUCTOS.authority(), PermissionArea.PRECIOS.authority(),
                                PermissionArea.STOCK.authority(), PermissionArea.COMBOS.authority())
                        // Productos: alta, edicion, borrado, imagenes, metadatos y destacados
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority(PermissionArea.PRODUCTOS.authority())
                        // Combos, incluido su precio
                        .requestMatchers(HttpMethod.POST, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bundles/**").hasAuthority(PermissionArea.COMBOS.authority())
                        .requestMatchers("/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/bundles/**").permitAll()
                        .requestMatchers("/products/images/**").permitAll()
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