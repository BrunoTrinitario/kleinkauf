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
    public static final String INVALID_RECEIPT = "The receipt must be a JPG, PNG or PDF file of 5 MB or less";
    public static final String RECEIPT_NOT_ALLOWED = "Receipts can only be uploaded while the order is waiting for payment and before it expires";
    public static final String TOO_MANY_RECEIPTS = "The order already has the maximum of %d receipts";
    public static final String RECEIPT_NOT_FOUND = "Receipt not found";
    public static final String CANCEL_NOT_ALLOWED = "The order can no longer be cancelled: the payment was already validated";
    public static final String INVALID_STATUS_CHANGE = "The order cannot change from %s to %s";
    public static final String REASON_REQUIRED = "A reason (up to 500 characters) is required to reject or cancel";
    public static final String ORDER_CHANGED = "The order was changed by someone else. Reload it and try again";
    public static final String STOCK_NOT_UPDATED = "The stock could not be updated right now. Please try again in a few minutes";
    public static final String INVALID_LOCAL_ITEMS = "The sale needs between 1 and %d items, each with type, id, a quantity between 1 and %d and, if given, a price of zero or greater";
    public static final String PRICE_REQUIRED = "These items have no price, enter one: %s";
    public static final String INVALID_DISCOUNT = "The discount must be zero or greater and not exceed the subtotal";
    public static final String INVALID_PAYMENT_METHOD = "The payment method is required (EFECTIVO, TRANSFERENCIA, DEBITO, CREDITO or OTRO)";
    public static final String INVALID_LOCAL_STATUS = "A store sale starts as PAGADO or ENTREGADO";
    public static final String INVALID_OPTIONAL_BUYER = "Buyer data is optional, but if given: name up to 100 characters, a valid email and a valid phone";
}
