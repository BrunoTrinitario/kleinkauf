package com.kleinkauf.observability.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/*
Plain WebSocket on /ws (no STOMP). The origin is not restricted here: the gateway already rejects origins that
belong to neither a store nor the platform, and without a ticket nobody gets in.
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
