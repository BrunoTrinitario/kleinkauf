package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.util.UserValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Reglas de nombre, email y contrasena de un usuario (las mismas que valida el frontend)
class UserValidatorTest {

    @Test
    void usuarioValido() {
        assertDoesNotThrow(() -> UserValidator.validateNewUser("Ana Pérez", "ana@mail.com", "secreta12"));
        assertDoesNotThrow(() -> UserValidator.validateNewUser("Ana", " ana@mail.com.ar ", "contraseña1"));
    }

    @Test
    void nombreVacioOLargo() {
        assertInvalid(() -> UserValidator.validateName(null), ErrorMessages.INVALID_NAME);
        assertInvalid(() -> UserValidator.validateName("   "), ErrorMessages.INVALID_NAME);
        assertInvalid(() -> UserValidator.validateName("a".repeat(101)), ErrorMessages.INVALID_NAME);
        assertDoesNotThrow(() -> UserValidator.validateName("a".repeat(100)));
    }

    @Test
    void emailInvalido() {
        for (String email : new String[]{null, "", "r", "ana@", "@mail.com", "ana@mail", "ana mail@mail.com", "ana@@mail.com"}) {
            assertInvalid(() -> UserValidator.validateEmail(email), ErrorMessages.INVALID_EMAIL);
        }
        assertInvalid(() -> UserValidator.validateEmail("a".repeat(250) + "@m.co"), ErrorMessages.INVALID_EMAIL);
    }

    @Test
    void contrasenaDebil() {
        for (String password : new String[]{null, "r", "abc1234", "solamenteletras", "12345678"}) {
            assertInvalid(() -> UserValidator.validatePassword(password), ErrorMessages.INVALID_PASSWORD);
        }
        // bcrypt ignora lo que pasa de 72 bytes
        assertInvalid(() -> UserValidator.validatePassword("a1".repeat(37)), ErrorMessages.INVALID_PASSWORD);
        assertDoesNotThrow(() -> UserValidator.validatePassword("a1".repeat(36)));
        assertDoesNotThrow(() -> UserValidator.validatePassword("abcd1234"));
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable executable, String message) {
        ApiException ex = assertThrows(ApiException.class, executable);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(message, ex.getMessage());
    }
}
