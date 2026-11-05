package com.kleinkauf.observability.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/*
Tickets to open the WS: the JWT never goes in the URL. They are stored in Redis (ws-ticket:<id>) and:
- expire after observability.ticket.ttl (30 s),
- work only once: they are read and deleted in the same operation (GETDEL), so two connections with the
  same ticket cannot both get in, even with several observability instances.
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
