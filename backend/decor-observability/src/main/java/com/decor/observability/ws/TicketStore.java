package com.decor.observability.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/*
Tickets para abrir el WS: el JWT nunca va en la URL. Se guardan en Redis (ws-ticket:<id>) y:
- vencen a los observability.ticket.ttl (30 s),
- sirven una sola vez: se leen y se borran en la misma operacion (GETDEL), asi que dos conexiones con el
  mismo ticket no pueden entrar las dos, aunque haya varias instancias de observability.
 */
@Component
public class TicketStore {

    static final String PREFIX = "ws-ticket:";
    private static final int TICKET_BYTES = 32;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public TicketStore(StringRedisTemplate redis, ObjectMapper mapper,
                       @Value("${observability.ticket.ttl:30s}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public String issue(WsTicket ticket) {
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(PREFIX + id, mapper.writeValueAsString(ticket), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar el ticket", e);
        }
        return id;
    }

    public Optional<WsTicket> redeem(String id) {
        if (id == null || id.isBlank() || id.length() > 128) {
            return Optional.empty();
        }
        String value;
        try {
            value = redis.opsForValue().getAndDelete(PREFIX + id);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(value, WsTicket.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
