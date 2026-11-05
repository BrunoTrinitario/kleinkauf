package com.kleinkauf.gateway.util;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ServerWebExchange;

/*
Requests on the management port (/health, /ready, /metrics). In WebFlux the gateway's WebFilters
(JWT, tenant, rate limit) also run on that port, so each one lets them through with this.
The port is internal: the gateway does not route it and it is not published in Docker.
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
