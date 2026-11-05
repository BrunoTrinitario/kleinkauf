package com.kleinkauf.product.dto.store;

// Blank or null name: falls back to the name derived from the identifier. theme: one of StoreTheme. A null theme or
// flag keeps the current value. socials: null keeps all of them; inside, a null network keeps it and a blank one
// removes it
public record UpdateStoreDto(
        String name,
        String theme,
        Boolean showIconInHeader,
        Boolean showIconAsFavicon,
        SocialLinks socials
) {
}
