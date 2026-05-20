package com.decor.gateway.util;

import com.decor.gateway.constants.CustomHeaders;
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
                .getFirst(CustomHeaders.INTERNAL_TOKEN_HEADER);

    }

}
