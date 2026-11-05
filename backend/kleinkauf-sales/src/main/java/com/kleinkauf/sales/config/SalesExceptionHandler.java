package com.kleinkauf.sales.config;

import com.kleinkauf.sales.constants.ErrorMessages;
import com.kleinkauf.sales.dto.PricesChangedDto;
import com.kleinkauf.sales.services.PricesChangedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/*
Sales errors with their own body. Goes before kleinkauf-common's GlobalExceptionHandler,
which only responds with the message.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SalesExceptionHandler {

    // 409 with the new prices so the buyer reviews the cart
    @ExceptionHandler(PricesChangedException.class)
    public ResponseEntity<PricesChangedDto> handle(PricesChangedException ex) {
        return new ResponseEntity<>(new PricesChangedDto(ErrorMessages.PRICES_CHANGED, ex.getChanges()), HttpStatus.CONFLICT);
    }
}
