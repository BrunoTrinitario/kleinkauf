package com.kleinkauf.sales.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/*
Random order number with the XXXX-XXXX format (e.g. K7QM-4X2P). Random so other people's orders cannot be
enumerated. No 0/O, 1/I/L so it can be dictated without confusion.
31^8 combinations: OrderService ensures uniqueness against the database.
 */
@Component
public class OrderNumberGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder number = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) number.append('-');
            number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }
}
