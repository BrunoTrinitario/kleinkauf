package com.decor.common.observability.servlet;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/*
Observabilidad de los servicios servlet (users, products, sales, observability, eureka):
- Etiqueta "tenant" en las metricas HTTP.
- Tenant y usuario en el MDC de los logs.
- El puerto de gestion (/health, /ready, /metrics) no pide login: es interno, el gateway no lo expone.
  Spring aplica la seguridad del servicio tambien a ese puerto, y sin esta regla las reglas del
  servicio (ej. anyRequest().authenticated()) lo cerrarian.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration")
public class DecorObservabilityServletAutoConfiguration {

    static final String MANAGEMENT_NAMESPACE = "management";

    @Bean
    @ConditionalOnMissingBean(ServerRequestObservationConvention.class)
    public TenantServerRequestObservationConvention tenantServerRequestObservationConvention() {
        return new TenantServerRequestObservationConvention();
    }

    @Bean
    public FilterRegistrationBean<LogContextFilter> logContextFilter() {
        FilterRegistrationBean<LogContextFilter> registration = new FilterRegistrationBean<>(new LogContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // true si la request llego por el puerto de gestion (contexto hijo de Actuator)
    public static boolean isManagementRequest(HttpServletRequest request) {
        WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
        return context instanceof WebServerApplicationContext webContext
                && WebServerApplicationContext.hasServerNamespace(webContext, MANAGEMENT_NAMESPACE);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
    static class ManagementSecurityConfig {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher(DecorObservabilityServletAutoConfiguration::isManagementRequest)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
    }
}
