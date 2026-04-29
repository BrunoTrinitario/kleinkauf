/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Exception.java to edit this template
 */

package com.decor.product.exceptions;

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
    /**
     * Creates a new instance of <code>ApiException</code> without detail message.
     */
    public ApiException() {
    }


    /**
     * Constructs an instance of <code>ApiException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public ApiException(String msg) {
        super(msg);
    }

    public ApiException(String msg, HttpStatus status) {
        super(msg);
        this.status = status;
    }

}
