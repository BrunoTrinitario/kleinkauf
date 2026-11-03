package com.kleinkauf.observability.ws;

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
- store.activity: pedidos nuevos, ventas del local, comprobantes y stock (eventos de sales y products), apenas pasan.
- store.usage: visitas al catalogo, productos mas vistos del dia y requests y errores, en cada lectura del recolector.
- store.users: los usuarios conectados (alguna request en los ultimos 5 minutos), en cada lectura del recolector.
 */
public final class Channels {

    public static final String PLATFORM_HEALTH = "platform.health";
    public static final String PLATFORM_TRAFFIC = "platform.traffic";
    public static final String PLATFORM_BUSINESS = "platform.business";
    public static final String PLATFORM_AUDIT = "platform.audit";
    public static final String STORE_AUDIT = "store.audit";
    public static final String STORE_ACTIVITY = "store.activity";
    public static final String STORE_USAGE = "store.usage";
    public static final String STORE_USERS = "store.users";

    static final Set<String> PLATFORM = Set.of(PLATFORM_HEALTH, PLATFORM_TRAFFIC, PLATFORM_BUSINESS, PLATFORM_AUDIT);
    static final Set<String> STORE = Set.of(STORE_AUDIT, STORE_ACTIVITY, STORE_USAGE, STORE_USERS);

    private Channels() {
    }

    public static boolean allowed(WsTicket ticket, String channel) {
        return ticket.isPlatform() ? PLATFORM.contains(channel) : STORE.contains(channel);
    }
}
