package com.kleinkauf.product.dto.bundles;

// Enables or disables a bundle: the state is explicit (PUT /bundles/{id} used to toggle without saying which)
public record BundleEnabledDto(
        Boolean enabled
) {
}
