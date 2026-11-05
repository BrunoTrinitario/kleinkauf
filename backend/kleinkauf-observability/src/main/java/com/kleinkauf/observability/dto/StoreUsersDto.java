package com.kleinkauf.observability.dto;

import java.time.Instant;
import java.util.List;

/*
A store's online users (store.users channel and GET /api/v1/observability/store/users): those who made
some request in the last 5 minutes, most recent to oldest. They are all the logged-in ones, customers too.
name: null if users did not answer.
 */
public record StoreUsersDto(long total, List<ConnectedUser> users) {

    public record ConnectedUser(Long userId, String name, Instant lastActivity) {
    }
}
