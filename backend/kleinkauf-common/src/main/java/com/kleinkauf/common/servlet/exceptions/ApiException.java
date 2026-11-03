package com.kleinkauf.common.servlet.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 *
 * @author bruno.trinitario
 */
@Getter
@Setter
public class ApiException extends Exception {

    private String errorMessage;
    private HttpStatus status;

    public ApiException() {
    }

    public ApiException(String msg) {
        super(msg);
    }

    public ApiException(String msg, HttpStatus status) {
        super(msg);
        this.status = status;
    }

}
