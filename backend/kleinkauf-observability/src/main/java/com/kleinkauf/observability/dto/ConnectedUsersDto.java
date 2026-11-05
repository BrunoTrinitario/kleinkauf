package com.kleinkauf.observability.dto;

import java.util.List;

/*
Users with some request in the last 5 minutes, in total and per store (most to fewest, counting visitors).
Superadmins appear with tenant "public". visitors: anonymous visitors of the stores (only counts).
 */
public record ConnectedUsersDto(long total, long visitors, List<TenantUsers> byTenant) {
    public record TenantUsers(String tenant, long users, long visitors) {
    }
}
