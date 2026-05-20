package com.decor.gateway.util;

import com.decor.gateway.constants.CustomHeaders;
import com.decor.gateway.factories.RequestTenantFactory;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;

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
                .getFirst(CustomHeaders.INTERNAL_TOKEN_HEADER);

    }

    public static String getTenantIdentifierFromRequest(ServerWebExchange exchange){
        String baseUrl = getBaseUrlFromRequest(exchange);
        return Parser.getParsedUrlFromRequest(baseUrl);
    }

    public static String getBaseUrlFromRequest(ServerWebExchange exchange) {
        URI uri = exchange.getRequest().getURI();
        return uri.getScheme() + "://" + uri.getAuthority();
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
