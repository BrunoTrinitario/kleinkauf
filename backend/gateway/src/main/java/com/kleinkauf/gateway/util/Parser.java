package com.kleinkauf.gateway.util;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.gateway.factories.RequestTenantFactory;
import org.springframework.web.server.ServerWebExchange;


public class Parser {
    public static String getParsedUrlFromRequest(String request){
        return request
                .replace("http://", "")
                .replace("https://", "")
                .replaceAll("\\.","_")
                .replace(":", "");
    }

    public static String getInternalTokenFromRequest(ServerWebExchange exchange) {
        return exchange.getRequest()
                .getHeaders()
                .getFirst(Headers.INTERNAL_TOKEN);

    }

    public static String getTenantIdentifierFromRequest(ServerWebExchange exchange){
        return exchange.getRequest().getHeaders().getOrigin();
    }

    public static String getBaseUrlFromRequest(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getOrigin();
    }

    public static String generateRequestIdentifier(ServerWebExchange exchange) {
        String tenantId = Parser.getTenantIdentifierFromRequest(exchange);
        String ip = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");
        if (ip == null || ip.isBlank())
             ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String path = exchange.getRequest().getURI().getPath();
        String pathType = RequestTenantFactory.generatePathType(path);
        return "rate_limit:"+pathType+":"+tenantId+"_"+ip;
    }

}
