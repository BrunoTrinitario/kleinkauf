package com.kleinkauf.observability.dto;

import java.time.Instant;
import java.util.List;

/*
Usuarios conectados de una tienda (canal store.users y GET /api/v1/observability/store/users): los que hicieron
alguna request en los ultimos 5 minutos, del mas reciente al mas viejo. Son todos los logueados, tambien clientes.
name: null si users no respondio.
 */
public record StoreUsersDto(long total, List<ConnectedUser> users) {

    public record ConnectedUser(Long userId, String name, Instant lastActivity) {
    }
}
