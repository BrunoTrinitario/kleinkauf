package com.kleinkauf.observability.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
WS abiertos en esta instancia y sus suscripciones. Manda los eventos a los suscriptos de cada canal:
  { "type": "event", "channel": "platform.health", "data": { ... }, "at": "2026-11-02T10:00:00Z" }
Los envios pasan por ConcurrentWebSocketSessionDecorator: el recolector, los eventos y las respuestas
escriben desde hilos distintos, y un cliente lento no traba a los demas (se lo desconecta).
 */
@Slf4j
@Component
public class SessionRegistry {

    // Tope de lo que se le puede acumular a un cliente lento antes de cortarlo
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    public record Client(WebSocketSession session, WsTicket ticket, Set<String> channels) {
    }

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final Clock clock;

    public SessionRegistry(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public void register(WebSocketSession session, WsTicket ticket) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        clients.put(session.getId(), new Client(safe, ticket, ConcurrentHashMap.newKeySet()));
    }

    public void remove(String sessionId) {
        clients.remove(sessionId);
    }

    public Client client(String sessionId) {
        return clients.get(sessionId);
    }

    public Collection<Client> clients() {
        return List.copyOf(clients.values());
    }

    public void publish(String channel, Object data) {
        send(channel, data, clients.values().stream().filter(c -> c.channels().contains(channel)).toList());
    }

    // Canales de la tienda: solo a los WS abiertos con un ticket de esa tienda
    public void publishToTenant(String channel, String tenant, Object data) {
        send(channel, data, clients.values().stream()
                .filter(c -> c.channels().contains(channel) && tenant.equals(c.ticket().tenant()))
                .toList());
    }

    // Tiendas con algun WS suscripto al canal: el recolector arma los datos de la tienda solo para esas
    public Set<String> tenantsSubscribedTo(String channel) {
        return clients.values().stream()
                .filter(c -> !c.ticket().isPlatform() && c.channels().contains(channel))
                .map(c -> c.ticket().tenant())
                .collect(Collectors.toSet());
    }

    private void send(String channel, Object data, List<Client> subscribed) {
        if (subscribed.isEmpty()) {
            return;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "event");
        message.put("channel", channel);
        message.put("data", data);
        message.put("at", clock.instant());
        TextMessage text;
        try {
            text = new TextMessage(mapper.writeValueAsString(message));
        } catch (IOException e) {
            log.warn("No se pudo armar el mensaje del canal {}: {}", channel, e.toString());
            return;
        }
        subscribed.forEach(client -> send(client, text));
    }

    public void send(Client client, TextMessage text) {
        try {
            client.session().sendMessage(text);
        } catch (Exception e) {
            // Cliente lento o conexion rota: se lo corta y el frontend se vuelve a conectar
            close(client, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    public void close(Client client, CloseStatus status) {
        clients.remove(client.session().getId());
        try {
            client.session().close(status);
        } catch (IOException ignored) {
            // Ya estaba cerrada
        }
    }
}
