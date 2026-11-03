package com.decor.observability.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/*
WebSocket puro en /ws (sin STOMP). El origen no se restringe aca: el gateway ya rechaza los origenes que no
son de una tienda ni de la plataforma, y sin ticket no se entra.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObservabilityWebSocketHandler handler;
    private final TicketHandshakeInterceptor ticketInterceptor;

    public WebSocketConfig(ObservabilityWebSocketHandler handler, TicketHandshakeInterceptor ticketInterceptor) {
        this.handler = handler;
        this.ticketInterceptor = ticketInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(ticketInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
