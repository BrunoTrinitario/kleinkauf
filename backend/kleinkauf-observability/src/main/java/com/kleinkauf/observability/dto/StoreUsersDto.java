package com.kleinkauf.observability.dto;

import java.time.Instant;
import java.util.List;

/*
Online users of a store (store.users channel and GET /api/v1/observability/store/users): those who made
some request in the last 5 minutes, from most recent to oldest. All logged-in users, customers too.
name: null if users did not respond. visitors: anonymous visitors (without a session) in the same 5 minutes; only the
count, they have no name.
 */
public record StoreUsersDto(long total, List<ConnectedUser> users, long visitors) {

    public record ConnectedUser(Long userId, String name, Instant lastActivity) {
    }
}
