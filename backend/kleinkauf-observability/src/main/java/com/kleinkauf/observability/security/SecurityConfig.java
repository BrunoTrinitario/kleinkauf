package com.kleinkauf.observability.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderAuthFilter headerAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // WS: entered with the ticket (TicketHandshakeInterceptor)
                        .requestMatchers("/ws").permitAll()
                        // Controller errors (400, 404) are answered through /error: otherwise they would go out as 403
                        .requestMatchers("/error").permitAll()
                        // Platform dashboard: superadmin only
                        .requestMatchers("/api/v1/platform/observability/**").hasRole(RolesService.SUPERADMIN)
                        // Store dashboard and audit: administrator only
                        .requestMatchers("/api/v1/observability/**").hasRole(RolesService.ADMINISTRADOR)
                        .anyRequest().denyAll()
                )
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // Only inside the security chain: without this Spring Boot would also register it as a standalone filter
    @Bean
    public FilterRegistrationBean<HeaderAuthFilter> headerAuthFilterRegistration(HeaderAuthFilter filter) {
        FilterRegistrationBean<HeaderAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
