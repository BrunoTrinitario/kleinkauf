package com.kleinkauf.common.servlet.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void fileTooLarge_responds413() {
        ResponseEntity<?> response = handler.handleAll(new MaxUploadSizeExceededException(5 * 1024 * 1024));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    }

    @Test
    void apiException_respondsWithItsStatus() {
        ResponseEntity<?> response = handler.handleAll(new ApiException("no existe", HttpStatus.NOT_FOUND));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("no existe", response.getBody());
    }
}
