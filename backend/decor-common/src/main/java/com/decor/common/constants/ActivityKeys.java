package com.decor.common.constants;

import java.time.Duration;

/*
Ultima actividad de cada usuario en Redis (docs/observabilidad.md): activity:<tenant>:<userId> con la hora
de su ultima request (epoch en milisegundos). La escribe el gateway y la lee observability.
Conectado = alguna request en los ultimos TTL: la key vence sola.
tenant: el schema de la tienda, o "public" para los superadmins.
 */
public final class ActivityKeys {

    public static final String PREFIX = "activity:";
    public static final Duration TTL = Duration.ofMinutes(5);

    private ActivityKeys() {
    }

    public static String key(String tenant, Long userId) {
        return PREFIX + tenant + ":" + userId;
    }

    // El tenant de una key, o null si no tiene la forma activity:<tenant>:<userId>
    public static String tenantOf(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        String rest = key.substring(PREFIX.length());
        int colon = rest.lastIndexOf(':');
        return colon <= 0 ? null : rest.substring(0, colon);
    }
}
