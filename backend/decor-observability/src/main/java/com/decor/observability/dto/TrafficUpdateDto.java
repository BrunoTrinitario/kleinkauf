package com.decor.observability.dto;

import java.time.Instant;
import java.util.List;

// Lo que manda el canal platform.traffic en cada lectura
public record TrafficUpdateDto(Instant at, List<ServiceTrafficDto> traffic, ConnectedUsersDto connectedUsers) {
}
