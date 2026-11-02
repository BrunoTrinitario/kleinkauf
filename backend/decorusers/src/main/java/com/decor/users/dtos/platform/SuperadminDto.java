package com.decor.users.dtos.platform;

import java.time.OffsetDateTime;

// active = false: desactivado (deleted_at en el usuario y en su rol SUPERADMIN)
public record SuperadminDto(
        Long id,
        String name,
        String email,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastLogin
) {
}
