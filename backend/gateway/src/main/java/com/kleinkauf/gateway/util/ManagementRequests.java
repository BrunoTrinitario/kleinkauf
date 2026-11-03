package com.kleinkauf.gateway.util;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ServerWebExchange;

/*
Requests del puerto de gestion (/health, /ready, /metrics). En WebFlux los WebFilter del gateway
(JWT, tenant, rate limit) tambien corren en ese puerto, asi que cada uno las deja pasar con esto.
El puerto es interno: el gateway no lo rutea y en Docker no se publica.
 */
public final class ManagementRequests {

    private static final String MANAGEMENT_NAMESPACE = "management";

    private ManagementRequests() {
    }

    public static boolean isManagement(ServerWebExchange exchange) {
        ApplicationContext context = exchange.getApplicationContext();
        return context instanceof WebServerApplicationContext webContext
                && WebServerApplicationContext.hasServerNamespace(webContext, MANAGEMENT_NAMESPACE);
    }
}
