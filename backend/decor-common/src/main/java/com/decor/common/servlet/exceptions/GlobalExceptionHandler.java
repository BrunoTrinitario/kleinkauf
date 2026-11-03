package com.decor.common.servlet.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 *
 * @author bruno.trinitario
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleAll(ApiException ex) {
        return new ResponseEntity<>(ex.getMessage(), ex.getStatus());
    }

    // Archivo mas grande que spring.servlet.multipart.max-file-size (antes caia en el 500 generico)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleAll(MaxUploadSizeExceededException ex) {
        return new ResponseEntity<>("The file exceeds the maximum upload size", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // Un error no previsto: se loguea con la traza (antes el 500 no dejaba rastro, ni siquiera con el cuerpo vacio)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception ex) {
        log.error("Unhandled error", ex);
        return new ResponseEntity<>(ex.getMessage(),  HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
