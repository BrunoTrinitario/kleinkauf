package com.kleinkauf.common.observability.servlet;

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
Observability of the servlet services (users, products, sales, observability, eureka):
- "tenant" tag on the HTTP metrics.
- Tenant and user in the logs' MDC.
- The management port (/health, /ready, /metrics) does not require login: it is internal, the gateway does not expose it.
  Spring applies the service's security to that port too, and without this rule the service's
  rules (e.g. anyRequest().authenticated()) would lock it.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration")
public class KleinKaufObservabilityServletAutoConfiguration {

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

    // true if the request came in through the management port (Actuator child context)
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
                    .securityMatcher(KleinKaufObservabilityServletAutoConfiguration::isManagementRequest)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
    }
}
