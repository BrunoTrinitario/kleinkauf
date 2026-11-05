package com.kleinkauf.observability.dto;

import java.util.List;

/*
Users with some request in the last 5 minutes, in total and per store (most to least).
Superadmins show with tenant "public".
 */
public record ConnectedUsersDto(long total, List<TenantUsers> byTenant) {

    public record TenantUsers(String tenant, long users) {
    }
}
