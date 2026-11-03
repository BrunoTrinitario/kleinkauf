package com.decor.observability.ws;

import com.decor.common.constants.Headers;
import com.decor.common.constants.TenantConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/*
Abre el WS solo con un ticket valido (/ws?ticket=...) y del mismo tenant que la conexion:
el gateway reconoce la tienda por el Origin y la pasa en X-Forwarded-Host; desde el frontend de la
plataforma no hay tienda y la conexion es de "public". Sin ticket, vencido o ya usado: 401.
De otro tenant (ej. un ticket de plataforma usado desde una tienda): 403.
 */
@Component
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    static final String TICKET_PARAM = "ticket";

    private final TicketStore tickets;

    public TicketHandshakeInterceptor(TicketStore tickets) {
        this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String id = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(TICKET_PARAM);
        WsTicket ticket = tickets.redeem(id).orElse(null);
        if (ticket == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String header = request.getHeaders().getFirst(Headers.FORWARDED_HOST);
        String connectionTenant = header == null || header.isBlank() ? TenantConstants.PUBLIC : header;
        if (!connectionTenant.equals(ticket.tenant())) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ObservabilityWebSocketHandler.TICKET_ATTRIBUTE, ticket);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
