package com.kleinkauf.users.dtos;

import java.util.List;

// A page of the dashboard user listing (same format as the products catalog)
public record UserPageDto(
        List<FullUserDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
