package com.kleinkauf.observability.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Mensajes del cliente (JSON):
  { "type": "subscribe", "channel": "platform.health" }  -> { "type": "subscribed", "channel": ... }
  { "type": "unsubscribe", "channel": "platform.health" } -> { "type": "unsubscribed", "channel": ... }
  { "type": "ping" }                                      -> { "type": "pong" }
Lo que no se entiende o un canal no permitido responde { "type": "error", "message": ... } sin cortar.
El ticket ya se valido en el handshake (TicketHandshakeInterceptor).
 */
@Component
public class ObservabilityWebSocketHandler extends TextWebSocketHandler {

    static final String TICKET_ATTRIBUTE = "kleinkauf.ws.ticket";
    private static final int MAX_MESSAGE_LENGTH = 1024;

    private final SessionRegistry sessions;
    private final ObjectMapper mapper;

    public ObservabilityWebSocketHandler(SessionRegistry sessions, ObjectMapper mapper) {
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.register(session, (WsTicket) session.getAttributes().get(TICKET_ATTRIBUTE));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionRegistry.Client client = sessions.client(session.getId());
        if (client == null) {
            return;
        }
        if (message.getPayloadLength() > MAX_MESSAGE_LENGTH) {
            reply(client, Map.of("type", "error", "message", "Mensaje demasiado largo"));
            return;
        }
        JsonNode json;
        try {
            json = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            reply(client, Map.of("type", "error", "message", "JSON invalido"));
            return;
        }
        String type = json.path("type").asText();
        String channel = json.path("channel").asText();
        switch (type) {
            case "ping" -> reply(client, Map.of("type", "pong"));
            case "subscribe" -> {
                if (!Channels.allowed(client.ticket(), channel)) {
                    reply(client, Map.of("type", "error", "message", "Canal no permitido: " + channel));
                    return;
                }
                client.channels().add(channel);
                reply(client, Map.of("type", "subscribed", "channel", channel));
            }
            case "unsubscribe" -> {
                client.channels().remove(channel);
                reply(client, Map.of("type", "unsubscribed", "channel", channel));
            }
            default -> reply(client, Map.of("type", "error", "message", "Tipo de mensaje desconocido: " + type));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    private void reply(SessionRegistry.Client client, Map<String, Object> body) throws Exception {
        sessions.send(client, new TextMessage(mapper.writeValueAsString(new LinkedHashMap<>(body))));
    }
}
