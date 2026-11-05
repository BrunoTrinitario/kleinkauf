package com.kleinkauf.sales.dto;

// Cancellation by the buyer: the order email identifies the guest
public record CancelDto(
        String email
) {
}
