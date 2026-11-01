/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.constants;

/**
 *
 * @author bruno.trinitario
 */
public class ErrorMessages {
    public static String USER_NOT_FOUND = "The user was not found.";
    public static String INVALID_CREDENTIALS = "Invalid username or password.";
    public static String EMAIL_ALREADY_IN_USE = "The email is already in use.";
    public static String INVALID_ROLE = "The role provided is invalid.";
    public static String ROLES_MUST_BE_EMPTY = "Should not be any role";
    public static String INVALID_TOKEN = "The access token is invalid.";
    public static String TENANT_NOT_FOUND = "The tenant was not found.";
    public static String INVALID_TENANT_IDENTIFIER = "The identifier must have 3 to 30 characters, start with a letter and only use lowercase letters, numbers and hyphens.";
    public static String TENANT_IDENTIFIER_RESERVED = "The identifier is reserved.";
    public static String TENANT_IDENTIFIER_IN_USE = "The identifier is already in use.";
    public static String OWNER_DATA_REQUIRED = "The owner name, email and password are required.";
    public static String PLATFORM_ONLY = "This endpoint is only available on the platform.";
}
