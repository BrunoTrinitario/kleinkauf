package com.kleinkauf.users.util;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/*
Rules for a user's data. Applied on every create (sign-up, creation from the dashboard,
owner of a new store) and on name or password changes. They are the same ones the
frontend shows (lib/validation.ts); if they change, change both.
- Name: required, up to 100 characters.
- Email: user@domain.ext format, no spaces, up to 254 characters.
- Password: 8 to 72 characters (bcrypt ignores anything past 72 bytes), with at least one letter and one digit.
 */
public final class UserValidator {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private UserValidator() {
    }

    // Full create: all three fields are required
    public static void validateNewUser(String name, String email, String password) throws ApiException {
        validateName(name);
        validateEmail(email);
        validatePassword(password);
    }

    public static void validateName(String name) throws ApiException {
        if (name == null || name.isBlank() || name.trim().length() > NAME_MAX_LENGTH) {
            throw new ApiException(ErrorMessages.INVALID_NAME, HttpStatus.BAD_REQUEST);
        }
    }

    public static void validateEmail(String email) throws ApiException {
        if (email == null || email.trim().length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email.trim()).matches()) {
            throw new ApiException(ErrorMessages.INVALID_EMAIL, HttpStatus.BAD_REQUEST);
        }
    }

    // The email is case-insensitive: stored and searched in lowercase
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static void validatePassword(String password) throws ApiException {
        if (password == null
                || password.length() < PASSWORD_MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES
                || !HAS_LETTER.matcher(password).find()
                || !HAS_DIGIT.matcher(password).find()) {
            throw new ApiException(ErrorMessages.INVALID_PASSWORD, HttpStatus.BAD_REQUEST);
        }
    }
}
