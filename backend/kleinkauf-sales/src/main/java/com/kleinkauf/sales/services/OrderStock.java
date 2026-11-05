package com.kleinkauf.sales.services;

import com.kleinkauf.common.dto.stock.ReservationDto;
import com.kleinkauf.common.dto.stock.ReservationItemDto;
import com.kleinkauf.common.dto.stock.ReservationRequestDto;
import com.kleinkauf.common.dto.stock.SoldItemsDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.constants.ErrorMessages;
import com.kleinkauf.sales.entities.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/*
An order's stock and sold counter in products (see ProductsClient).
- products' 4xx (no stock, no price, not available) arrive with their message and status.
- The rest (network, 5xx, open circuit) responds 503: products could not be updated and it must be retried.
The "quietly" variants are used to roll back: if they fail, it is logged so it can be fixed manually
(stock movements carry the order number as reference).
 */
@Slf4j
@Component
public class OrderStock {

    @Autowired
    private ProductsClient productsClient;

    /*
    Reserves all or nothing. If products does not answer the reservation may have been made: it is released just in case
    (products ignores the release of a reservation that does not exist and rejects the one arriving later).
     */
    public ReservationDto reserve(String number, List<ReservationItemDto> items, boolean local) throws ApiException {
        try {
            ReservationDto reservation = productsClient.reserve(new ReservationRequestDto(number, items, local));
            if (reservation == null || reservation.items() == null || reservation.items().size() != items.size()) {
                throw new IllegalStateException("Respuesta de reserva invalida para el pedido " + number);
            }
            return reservation;
        } catch (HttpClientErrorException e) {
            throw clientError(e);
        } catch (RuntimeException e) {
            log.warn("No se pudo reservar el stock del pedido {}: {}", number, e.toString());
            releaseQuietly(number);
            throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void release(String number) throws ApiException {
        try {
            productsClient.release(number);
        } catch (HttpClientErrorException e) {
            throw clientError(e);
        } catch (RuntimeException e) {
            log.warn("No se pudo devolver el stock del pedido {}: {}", number, e.toString());
            throw new ApiException(ErrorMessages.STOCK_NOT_UPDATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void releaseQuietly(String number) {
        try {
            productsClient.release(number);
        } catch (RuntimeException e) {
            log.error("No se pudo devolver el stock del pedido {}: revisar los movimientos con esa referencia", number, e);
        }
    }

    public void markSold(Order order) throws ApiException {
        markSold(order.getNumber(), soldItems(order));
    }

    public void markSold(String number, List<ReservationItemDto> items) throws ApiException {
        try {
            productsClient.markSold(number, new SoldItemsDto(items));
        } catch (HttpClientErrorException e) {
            throw clientError(e);
        } catch (RuntimeException e) {
            log.warn("No se pudo sumar a vendidos el pedido {}: {}", number, e.toString());
            throw new ApiException(ErrorMessages.STOCK_NOT_UPDATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void unmarkSold(Order order) throws ApiException {
        try {
            productsClient.unmarkSold(order.getNumber(), new SoldItemsDto(soldItems(order)));
        } catch (HttpClientErrorException e) {
            throw clientError(e);
        } catch (RuntimeException e) {
            log.warn("No se pudo restar de vendidos el pedido {}: {}", order.getNumber(), e.toString());
            throw new ApiException(ErrorMessages.STOCK_NOT_UPDATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void unmarkSoldQuietly(String number, List<ReservationItemDto> items) {
        try {
            productsClient.unmarkSold(number, new SoldItemsDto(items));
        } catch (RuntimeException e) {
            log.error("No se pudo restar de vendidos el pedido {}: revisar el contador de sus productos", number, e);
        }
    }

    static List<ReservationItemDto> soldItems(Order order) {
        return order.getItems().stream()
                .map(i -> new ReservationItemDto(i.getType(), i.getItemId(), i.getQuantity()))
                .toList();
    }

    private static ApiException clientError(HttpClientErrorException e) {
        HttpStatus status = e.getStatusCode().value() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return new ApiException(e.getResponseBodyAsString(), status);
    }
}
