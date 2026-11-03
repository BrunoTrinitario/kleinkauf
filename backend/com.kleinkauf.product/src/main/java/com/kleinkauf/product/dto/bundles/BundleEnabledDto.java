package com.kleinkauf.product.dto.bundles;

// Habilita o deshabilita un combo: el estado va explicito (antes PUT /bundles/{id} alternaba sin decir a cual)
public record BundleEnabledDto(
        Boolean enabled
) {
}
