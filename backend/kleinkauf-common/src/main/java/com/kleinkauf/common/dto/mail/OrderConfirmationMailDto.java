package com.kleinkauf.common.dto.mail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
What sales sends to users (POST /api/internal/mail/order-confirmation) so it emails the buyer of an online order.
users adds the store's name, contact and URL. deliveryAddress is null for pickup; payment is the store's transfer
details (any field may be null).
 */
public record OrderConfirmationMailDto(
        String number,
        String buyerName,
        String buyerEmail,
        List<Item> items,
        BigDecimal discount,
        BigDecimal total,
        String deliveryType,
        String deliveryAddress,
        LocalDateTime expiresAt,
        Payment payment
) {

    public record Item(String name, int quantity, BigDecimal subtotal) {
    }

    public record Payment(String alias, String cbu, String holder, String bank) {
    }
}
