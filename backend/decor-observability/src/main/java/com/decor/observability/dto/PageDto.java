package com.decor.observability.dto;

import java.util.List;

// Pagina de un listado, con la misma forma que los otros servicios (Page<T> del frontend). page empieza en 0
public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
