package com.kleinkauf.product.dto;

/*
featured: whether the product is shown as featured.
order: position among the featured products (optional, 0 or more). Ignored if featured is false.
 */
public record FeaturedDto(
        boolean featured,
        Integer order
) {
}
