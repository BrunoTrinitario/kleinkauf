/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.kleinkauf.users.constants;

/**
 *
 * @author bruno.trinitario
 */
public class ErrorMessages {
    public static String USER_NOT_FOUND = "The user was not found.";
    public static String INVALID_CREDENTIALS = "Invalid username or password.";
    public static String EMAIL_ALREADY_IN_USE = "The email is already in use.";
    public static String INVALID_ROLE = "The role provided is invalid.";
    public static String INVALID_NAME = "The name is required and can have up to 100 characters.";
    public static String INVALID_EMAIL = "The email is not valid.";
    public static String INVALID_PASSWORD = "The password must have 8 to 72 characters, with at least one letter and one number.";
    public static String INVALID_PAGE ="Invalid page: page must be 0 or more and size between 1 and 100.";
    public static String ROLES_MUST_BE_EMPTY = "Should not be any role";
    public static String INVALID_TOKEN = "The access token is invalid.";
    public static String TENANT_NOT_FOUND = "The tenant was not found.";
    public static String INVALID_TENANT_IDENTIFIER = "The identifier must have 3 to 30 characters, start with a letter and only use lowercase letters, numbers and hyphens.";
    public static String TENANT_IDENTIFIER_RESERVED = "The identifier is reserved.";
    public static String TENANT_IDENTIFIER_IN_USE = "The identifier is already in use.";
    public static String OWNER_DATA_REQUIRED = "The owner name, email and password are required.";
    public static String PLATFORM_ONLY = "This endpoint is only available on the platform.";
    public static String ROLE_PERMISSIONS_NOT_EDITABLE = "Only the EMPLEADO and CLIENTE permissions can be configured.";
    public static String INVALID_FILTER = "Invalid value for %s.";
    public static String INVALID_PERIOD_RANGE = "Invalid range: from must not be after to, with up to %d periods.";
    public static String INVALID_SORT = "Invalid sort: use users, activeUsers, lastActivity or createdAt.";
    public static String SUPERADMIN_NOT_FOUND = "The superadmin was not found.";
    public static String CANNOT_DEACTIVATE_SELF = "You cannot deactivate yourself.";
    public static String LAST_ACTIVE_SUPERADMIN = "The last active superadmin cannot be deactivated.";
    public static String LAST_ADMINISTRATOR = "The store needs at least one ADMINISTRADOR: the last one cannot be deleted or lose the role.";
    public static String INVALID_PERMISSIONS ="Invalid permissions: use PRODUCTOS=1, PRECIOS=2, STOCK=4 and COMBOS=8 (USUARIOS cannot be delegated).";
}
