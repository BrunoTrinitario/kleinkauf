package com.kleinkauf.product.dto.csv;

import java.util.List;

/*
Result of an import. It is all or nothing: if errors is not empty nothing was imported.
row is the line number in the file (1 = headers); 0 if the error is about the whole file.
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
