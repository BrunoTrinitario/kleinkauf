package com.kleinkauf.sales.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/*
Numero de pedido aleatorio con formato XXXX-XXXX (ej. K7QM-4X2P). Aleatorio para que no se puedan
recorrer los pedidos de otros. Sin 0/O, 1/I/L para que se pueda dictar sin confusiones.
31^8 combinaciones: la unicidad la asegura OrderService contra la base.
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
