package com.kleinkauf.users.dtos;

import java.util.List;

// Pagina del listado de usuarios del panel (mismo formato que el catalogo de products)
public record UserPageDto(
        List<FullUserDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
