package com.decor.observability.ws;

import java.util.Set;

/*
Canales del WS (docs/observabilidad.md). Por ahora solo los de la plataforma, para el superadmin:
- platform.health: la salud de los servicios, cuando cambia algun estado.
- platform.traffic: trafico, recursos y usuarios conectados, en cada lectura del recolector.
- platform.business: altas, bajas y cambios de tiendas (eventos de users).
Los de la tienda (store.*) y platform.audit llegan con la auditoria (etapa 3).
 */
public final class Channels {

    public static final String PLATFORM_HEALTH = "platform.health";
    public static final String PLATFORM_TRAFFIC = "platform.traffic";
    public static final String PLATFORM_BUSINESS = "platform.business";

    static final Set<String> PLATFORM = Set.of(PLATFORM_HEALTH, PLATFORM_TRAFFIC, PLATFORM_BUSINESS);

    private Channels() {
    }

    public static boolean allowed(WsTicket ticket, String channel) {
        return ticket.isPlatform() && PLATFORM.contains(channel);
    }
}
