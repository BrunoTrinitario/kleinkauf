package com.kleinkauf.product.dto.csv;

import java.util.List;

/*
Resultado de una importacion. Es todo o nada: si errors no esta vacio no se importo nada.
row es el numero de linea del archivo (1 = encabezados); 0 si el error es del archivo entero.
 */
public record ImportResultDto(
        int created,
        int updated,
        List<RowError> errors
) {
    public record RowError(int row, String message) {
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
