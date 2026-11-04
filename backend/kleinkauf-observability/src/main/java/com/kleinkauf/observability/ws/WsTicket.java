package com.kleinkauf.observability.ws;

import com.kleinkauf.common.constants.TenantConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;

/*
Who opens the WS: the user and the tenant with which they requested the ticket ("public" for the superadmin).
 */
public record WsTicket(Long userId, String tenant) {

    @JsonIgnore
    public boolean isPlatform() {
        return TenantConstants.PUBLIC.equals(tenant);
    }
}
