package com.decor.product.util;

import com.decor.product.constants.ErrorMessages;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;

public class ResponseUtil {

    public static HttpServletResponse tenantNotFoundResponse(HttpServletResponse response){
        try{
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write(ErrorMessages.TENANT_NOT_FOUND);
        }catch(IOException e){
        }
        return response;
    }
}
