package com.decor.observability.dto;

import java.time.Instant;
import java.util.List;

/*
Ultima lectura del recolector (GET /api/v1/platform/observability/overview). El panel la pide al abrir y
despues se actualiza por el WS. at: null si todavia no hubo ninguna lectura.
connectedUsers: null si no se pudo leer Redis.
 */
public record PlatformOverviewDto(Instant at, List<ServiceHealthDto> services, List<ServiceTrafficDto> traffic,
                                  ConnectedUsersDto connectedUsers) {

    public static PlatformOverviewDto empty() {
        return new PlatformOverviewDto(null, List.of(), List.of(), null);
    }
}
