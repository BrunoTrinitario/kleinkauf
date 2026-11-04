package com.kleinkauf.observability.dto;

import java.time.Instant;
import java.util.List;

// What the platform.traffic channel sends on each reading
public record TrafficUpdateDto(Instant at, List<ServiceTrafficDto> traffic, ConnectedUsersDto connectedUsers) {
}
