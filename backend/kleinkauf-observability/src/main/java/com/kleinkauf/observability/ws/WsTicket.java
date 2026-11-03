package com.kleinkauf.observability.ws;

import com.kleinkauf.common.constants.TenantConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;

/*
Quien abre el WS: el usuario y el tenant con que pidio el ticket ("public" para el superadmin).
 */
public record WsTicket(Long userId, String tenant) {

    @JsonIgnore
    public boolean isPlatform() {
        return TenantConstants.PUBLIC.equals(tenant);
    }
}
