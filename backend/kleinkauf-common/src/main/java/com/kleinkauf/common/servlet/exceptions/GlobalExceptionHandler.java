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

    // File larger than spring.servlet.multipart.max-file-size (it used to fall into the generic 500)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleAll(MaxUploadSizeExceededException ex) {
        return new ResponseEntity<>("The file exceeds the maximum upload size", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // Request errors that used to fall into the generic 500: a route that does not exist, a method the route does not have,
    // a parameter of another type (e.g. /products/all against /products/{id}) or a body that is not valid JSON
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

    // An unexpected error: logged with the stack trace (the 500 used to leave no trace, not even with an empty body)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception ex) {
        log.error("Unhandled error", ex);
        return new ResponseEntity<>(ex.getMessage(),  HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
