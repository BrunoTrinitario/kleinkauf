package com.kleinkauf.common.servlet.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 *
 * @author bruno.trinitario
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String NOT_FOUND = "Not found";
    static final String METHOD_NOT_ALLOWED = "Method not allowed";
    static final String INVALID_VALUE = "Invalid value for %s";
    static final String INVALID_BODY = "The request body is missing or is not valid JSON";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleAll(ApiException ex) {
        return new ResponseEntity<>(ex.getMessage(), ex.getStatus());
    }

    // Archivo mas grande que spring.servlet.multipart.max-file-size (antes caia en el 500 generico)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleAll(MaxUploadSizeExceededException ex) {
        return new ResponseEntity<>("The file exceeds the maximum upload size", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // Errores del pedido que antes caian en el 500 generico: una ruta que no existe, un metodo que la ruta no tiene,
    // un parametro con otro tipo (ej. /products/all contra /products/{id}) o un cuerpo que no es JSON valido
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleAll(NoResourceFoundException ex) {
        return new ResponseEntity<>(NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleAll(HttpRequestMethodNotSupportedException ex) {
        return new ResponseEntity<>(METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleAll(MethodArgumentTypeMismatchException ex) {
        return new ResponseEntity<>(String.format(INVALID_VALUE, ex.getName()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleAll(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>(INVALID_BODY, HttpStatus.BAD_REQUEST);
    }

    // Un error no previsto: se loguea con la traza (antes el 500 no dejaba rastro, ni siquiera con el cuerpo vacio)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception ex) {
        log.error("Unhandled error", ex);
        return new ResponseEntity<>(ex.getMessage(),  HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
