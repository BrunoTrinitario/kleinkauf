package com.decor.observability.ws;

import java.util.Set;

/*
Canales del WS (docs/observabilidad.md).
Plataforma (superadmin, ticket de public):
- platform.health: la salud de los servicios, cuando cambia algun estado.
- platform.traffic: trafico, recursos y usuarios conectados, en cada lectura del recolector.
- platform.business: altas, bajas y cambios de tiendas (eventos de users).
- platform.audit: la auditoria de la plataforma, apenas se guarda.
Tienda (administrador, ticket de su tienda): siempre de la tienda del ticket, no se puede pedir otra.
- store.audit: la auditoria de su tienda, apenas se guarda.
 */
public final class Channels {

    public static final String PLATFORM_HEALTH = "platform.health";
    public static final String PLATFORM_TRAFFIC = "platform.traffic";
    public static final String PLATFORM_BUSINESS = "platform.business";
    public static final String PLATFORM_AUDIT = "platform.audit";
    public static final String STORE_AUDIT = "store.audit";

    static final Set<String> PLATFORM = Set.of(PLATFORM_HEALTH, PLATFORM_TRAFFIC, PLATFORM_BUSINESS, PLATFORM_AUDIT);
    static final Set<String> STORE = Set.of(STORE_AUDIT);

    private Channels() {
    }

    public static boolean allowed(WsTicket ticket, String channel) {
        return ticket.isPlatform() ? PLATFORM.contains(channel) : STORE.contains(channel);
    }
}
