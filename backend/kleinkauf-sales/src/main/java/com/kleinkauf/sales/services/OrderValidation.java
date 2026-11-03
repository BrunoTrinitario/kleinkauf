package com.kleinkauf.sales.services;

import com.kleinkauf.sales.dto.AddressDto;
import com.kleinkauf.sales.dto.BuyerDto;
import com.kleinkauf.sales.entities.Address;
import com.kleinkauf.sales.entities.Order;

import java.util.Locale;
import java.util.regex.Pattern;

// Validaciones y armado de los datos del comprador y la entrega, comunes al pedido online y al del local
public class OrderValidation {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9 +()-]{6,30}$");

    public static boolean isValidName(String name) {
        return !isBlank(name) && name.trim().length() <= 100;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.trim().length() <= 150 && EMAIL.matcher(email.trim()).matches();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone.trim()).matches();
    }

    public static boolean isValid(AddressDto a) {
        if (a == null) return false;
        for (String required : new String[]{a.street(), a.number(), a.city(), a.province(), a.postalCode()}) {
            if (isBlank(required) || required.trim().length() > 100) return false;
        }
        return a.apartment() == null || a.apartment().trim().length() <= 100;
    }

    public static boolean isValidNotes(String notes) {
        return notes == null || notes.length() <= 500;
    }

    // Copia el comprador al pedido; los campos vacios quedan en null (en el local son opcionales)
    public static void setBuyer(Order order, BuyerDto buyer) {
        if (buyer == null) return;
        order.setBuyerName(trimToNull(buyer.name()));
        order.setBuyerEmail(isBlank(buyer.email()) ? null : buyer.email().trim().toLowerCase(Locale.ROOT));
        order.setBuyerPhone(trimToNull(buyer.phone()));
    }

    public static Address toAddress(AddressDto a) {
        return new Address(a.street().trim(), a.number().trim(), trimToNull(a.apartment()),
                a.city().trim(), a.province().trim(), a.postalCode().trim());
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
