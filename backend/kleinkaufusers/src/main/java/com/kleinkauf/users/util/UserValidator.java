package com.kleinkauf.users.util;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/*
Reglas de los datos de un usuario. Se aplican en todas las altas (registro, alta desde el panel,
dueno de una tienda nueva) y en los cambios de nombre o contrasena. Son las mismas que muestra el
frontend (lib/validation.ts); si cambian, cambiar las dos.
- Nombre: obligatorio, hasta 100 caracteres.
- Email: formato usuario@dominio.ext, sin espacios, hasta 254 caracteres.
- Contrasena: 8 a 72 caracteres (bcrypt ignora lo que pasa de 72 bytes), con al menos una letra y un numero.
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

    // Alta completa: los tres datos son obligatorios
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

    // El email no distingue mayusculas: se guarda y se busca en minuscula
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
