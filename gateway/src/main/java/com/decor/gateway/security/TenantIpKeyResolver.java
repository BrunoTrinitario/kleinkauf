package com.decor.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;

@Component
@RequiredArgsConstructor
public class TenantIpKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return null;
    }

    /*
       TODO:
        La idea sera aprovechar redis al mango para eso se tiene que:
        1. configurar este filtro con la consulta que necesitemos
        1.5. en mi caso es Tenant -> luego ip -> (rutas especificas)
        2. Para setear el rate limit, se hace en el app.properties
        keys/values
        key   = rate_limit:tenant_123:181.45.23.100 (max por tenant 1000)
        value = 57 (max por ip 70) (depende del endpoint)
        ttl   = 60 segundos
        "rate_limit:global:tenant_123:181.45.23.100": 42,
        "rate_limit:login:tenant_123:181.45.23.100": 3
     */

    //private static final String DEFAULT_TENANT = "anonymous";
    //private static final String DEFAULT_IP = "unknown";
//
    //@Override
    //public Mono<String> resolve(ServerWebExchange exchange) {
    //    URI uri = exchange.getRequest().getURI();
    //    String baseUrl = uri.getScheme() + "://" + uri.getAuthority();
    //    String tenantIdentifier = baseUrl
    //            .replace("http://", "")
    //            .replace("https://", "")
    //            .replace(":", "");
//
    //    if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
    //        tenantIdentifier = DEFAULT_TENANT;
    //    }
    //    String ip = exchange.getRequest()
    //            .getHeaders()
    //            .getFirst("X-Forwarded-For");
//
    //    if (ip != null && ip.contains(",")) {
    //        ip = ip.split(",")[0].trim();
    //    }
//
    //    if (ip == null || ip.isBlank()) {
    //        InetSocketAddress remoteAddress =
    //                exchange.getRequest().getRemoteAddress();
//
    //        ip = remoteAddress != null
    //                ? remoteAddress.getAddress().getHostAddress()
    //                : DEFAULT_IP;
    //    }
//
    //    return Mono.just("rate_limit:" + tenantIdentifier + ":" + ip);
    //}
}
