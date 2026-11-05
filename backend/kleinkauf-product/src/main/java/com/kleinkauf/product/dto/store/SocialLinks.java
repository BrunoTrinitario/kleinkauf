package com.kleinkauf.product.dto.store;

/*
The store's social networks: the full https link of each profile, or null if the store does not have one (the
storefront only shows the icons of the ones that are set). In an update, a null network keeps the current link and a
blank one removes it.
 */
public record SocialLinks(
        String instagram,
        String x,
        String facebook,
        String tiktok
) {
}
