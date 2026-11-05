package com.kleinkauf.common.observability.servlet;

import com.kleinkauf.common.constants.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
Puts the request's tenant and user in the MDC: they show up on every log line
(along with the traceId) and allow filtering the logs of a store or a user.
 */
public class LogContextFilter extends OncePerRequestFilter {

    public static final String TENANT = "tenant";
    public static final String USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        put(TENANT, request.getHeader(Headers.FORWARDED_HOST));
        put(USER_ID, request.getHeader(Headers.USER_ID));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TENANT);
            MDC.remove(USER_ID);
        }
    }

    private static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
