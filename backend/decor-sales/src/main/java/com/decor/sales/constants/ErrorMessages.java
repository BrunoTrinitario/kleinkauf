package com.decor.sales.constants;

public class ErrorMessages {
    public static final String INVALID_ITEMS = "The order needs between 1 and %d items, each with type, id, price and a quantity between 1 and %d";
    public static final String INVALID_BUYER = "Name (up to 100 characters), a valid email and a phone are required";
    public static final String INVALID_DELIVERY = "The delivery type is required (RETIRO or ENVIO)";
    public static final String INVALID_ADDRESS = "Shipping needs street, number, city, province and postal code (up to 100 characters each)";
    public static final String INVALID_NOTES = "Notes must be 500 characters or fewer";
    public static final String PAYMENT_NOT_CONFIGURED = "The store has not configured its payment details yet";
    public static final String PRICES_CHANGED = "Some prices changed. Please review the order and confirm again";
    public static final String ORDER_NOT_FOUND = "Order not found";
    public static final String ORDER_NOT_CREATED = "The order could not be created right now. Please try again in a few minutes";
    public static final String INVALID_SETTINGS = "Alias must have 6 to 20 letters, numbers, dots or hyphens, CBU 22 digits, holder and bank up to 100 characters and the expiration between 1 and 720 hours";
    public static final String INVALID_FILTER = "Invalid filter: %s";
    public static final String INVALID_PAGE = "The page must be 0 or greater and the size between 1 and 100";
}
