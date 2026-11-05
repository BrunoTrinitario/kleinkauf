package com.kleinkauf.observability.dto;

import java.time.Instant;
import java.util.List;

/*
The collector's last reading (GET /api/v1/platform/observability/overview). The dashboard requests it on open and
then it is updated over the WS. at: null if there has been no reading yet.
connectedUsers: null if Redis could not be read.
 */
public record PlatformOverviewDto(Instant at, List<ServiceHealthDto> services, List<ServiceTrafficDto> traffic,
                                  ConnectedUsersDto connectedUsers) {

    public static PlatformOverviewDto empty() {
        return new PlatformOverviewDto(null, List.of(), List.of(), null);
    }
}
