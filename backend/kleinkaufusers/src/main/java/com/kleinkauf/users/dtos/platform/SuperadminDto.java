package com.kleinkauf.users.dtos.platform;

import java.time.OffsetDateTime;

// active = false: deactivated (deleted_at on the user and on their SUPERADMIN role)
public record SuperadminDto(
        Long id,
        String name,
        String email,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastLogin
) {
}
