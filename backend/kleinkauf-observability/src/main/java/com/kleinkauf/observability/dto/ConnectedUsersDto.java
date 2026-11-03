package com.kleinkauf.observability.dto;

import java.util.List;

/*
Usuarios con alguna request en los ultimos 5 minutos, en total y por tienda (de mas a menos).
Los superadmins figuran con tenant "public".
 */
public record ConnectedUsersDto(long total, List<TenantUsers> byTenant) {

    public record TenantUsers(String tenant, long users) {
    }
}
