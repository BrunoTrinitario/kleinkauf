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
Stock y contador de vendidos de un pedido en products (ver ProductsClient).
- Los 4xx de products (sin stock, sin precio, no disponible) llegan con su mensaje y status.
- El resto (red, 5xx, circuito abierto) responde 503: products no se pudo actualizar y hay que reintentar.
Las variantes "quietly" se usan para volver atras: si fallan, se loguea para corregirlo a mano
(los movimientos de stock tienen el numero del pedido como referencia).
 */
@Slf4j
@Component
public class OrderStock {

    @Autowired
    private ProductsClient productsClient;

    /*
    Reserva todo o nada. Si products no responde la reserva pudo haberse hecho: se devuelve por las dudas
    (products ignora la devolucion de una reserva que no existe y rechaza la que llegue despues).
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
