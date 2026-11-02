package com.decor.sales.security;

import com.decor.common.constants.Headers;
import com.decor.common.dto.RoleDto;
import com.decor.common.security.InternalTokenService;
import com.decor.common.servlet.security.CustomUserDetails;
import com.decor.sales.constants.PermittedUrls;
import com.decor.sales.services.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/*
Arma el usuario autenticado con el header X-User-Id que agrega el gateway y sus roles de users.
En las rutas publicas tambien: crear un pedido logueado lo asocia al usuario.
/api/internal/** pide el token interno de los microservicios.
 */
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    // Authority de las llamadas entre microservicios (ver SecurityConfig)
    public static final String INTERNAL_ROLE = "ROLE_INTERNAL";
    private static final String INTERNAL_PRINCIPAL = "internal";

    @Autowired
    UserService userService;
    @Autowired
    InternalTokenService internalTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader(Headers.USER_ID);
        boolean isInternal = Boolean.parseBoolean(request.getHeader(Headers.INTERNAL_REQUEST));

        if (isInternal || request.getRequestURI().startsWith(PermittedUrls.INTERNAL_PATH)) {
            String token = request.getHeader(Headers.INTERNAL_TOKEN);
            if (internalTokenService.validateInternalToken(token)) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        INTERNAL_PRINCIPAL, null, List.of(new SimpleGrantedAuthority(INTERNAL_ROLE))));
                filterChain.doFilter(request, response);
            } else {
                rejectRequest(response, "Token invalido");
            }
            return;
        }

        if (userIdHeader != null) {
            Long userId = Long.parseLong(userIdHeader);
            List<RoleDto> roles = userService.getUserRolesById(userId);
            CustomUserDetails userDetails = new CustomUserDetails(userId, roles);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()));
        }

        filterChain.doFilter(request, response);
    }

    private void rejectRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write("""
        {
          "status": 403,
          "error": "Forbidden",
          "message": "%s"
        }
        """.formatted(message));

        response.getWriter().flush();
    }
}
