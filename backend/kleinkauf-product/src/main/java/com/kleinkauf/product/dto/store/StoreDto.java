package com.kleinkauf.product.dto.store;

/*
Store settings. Read by the public (GET /api/v1/catalog/store) and by the administrator
(GET /api/v1/store/settings). name is never null: without a custom name it is the one derived from the identifier;
customName is the one the administrator set (null if none). icon: file under /products/images/ or null.
 */
public record StoreDto(
        String name,
        String customName,
        String theme,
        String icon,
        boolean showIconInHeader,
        boolean showIconAsFavicon
) {
}
