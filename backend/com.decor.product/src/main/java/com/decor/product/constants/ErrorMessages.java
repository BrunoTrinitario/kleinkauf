/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.product.constants;

/**
 *
 * @author bruno.trinitario
 */
public class ErrorMessages {
    public static String PRODUCT_NOT_FOUND = "Product not found";
    public static String IMAGE_NOT_FOUND = "Image not found";
    public static String PRODUCT_NAME_IN_USE = "The Product name is already in use";
    public static String TENANT_NOT_FOUND = "Tenant not found";
    public static String BUNDLE_NOT_FOUND = "Bundle not found";
    public static String INVALID_PRICE = "Price and cost must be zero or greater";
    public static String INVALID_STOCK_MOVEMENT_TYPE = "The movement type is required (ENTRADA, SALIDA or AJUSTE)";
    public static String INVALID_STOCK_QUANTITY = "The quantity must be greater than zero (zero or greater for AJUSTE)";
    public static String STOCK_ADJUSTMENT_REASON_REQUIRED = "A reason is required for a stock adjustment";
    public static String INSUFFICIENT_STOCK = "Not enough stock";
    public static String INVALID_MIN_STOCK = "The minimum stock must be zero or greater";
    public static String INVALID_FEATURED_ORDER = "The featured order must be zero or greater";
    public static String IMAGE_TOO_LARGE = "Each image must be 1 MB or smaller";
    public static String INVALID_CATALOG_SORT = "The sort must be name, price_asc or price_desc";
    public static String INVALID_CATALOG_PAGE = "The page must be 0 or greater and the size between 1 and 100";
    public static String INVALID_RESERVATION = "The reservation needs a reference (up to 40 characters) and items with type, id and a quantity greater than zero";
    public static String RESERVATION_RELEASED = "The reservation was already released";
    public static String RESERVATION_IN_PROGRESS = "The reservation is being processed by another request";
    public static String ITEM_NOT_AVAILABLE = "Item not available";
    public static String ITEM_WITHOUT_PRICE = "Item without price";

}
