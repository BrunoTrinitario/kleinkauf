package com.kleinkauf.observability.dto;

import java.util.List;

// A page of a listing, with the same shape as the other services (the frontend's Page<T>). page starts at 0
public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
