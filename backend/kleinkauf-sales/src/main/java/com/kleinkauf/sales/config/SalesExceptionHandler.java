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
Errores de sales con cuerpo propio. Va antes que el GlobalExceptionHandler de kleinkauf-common,
que responde solo el mensaje.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SalesExceptionHandler {

    // 409 con los precios nuevos para que el comprador revise el carrito
    @ExceptionHandler(PricesChangedException.class)
    public ResponseEntity<PricesChangedDto> handle(PricesChangedException ex) {
        return new ResponseEntity<>(new PricesChangedDto(ErrorMessages.PRICES_CHANGED, ex.getChanges()), HttpStatus.CONFLICT);
    }
}
