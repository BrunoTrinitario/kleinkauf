package com.decor.sales.services;

import com.decor.common.constants.Headers;
import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationRequestDto;
import com.decor.common.dto.stock.SoldItemsDto;
import com.decor.common.security.InternalTokenService;
import com.decor.common.servlet.tenant.TenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/*
Reservas de stock y contador de vendidos en products (endpoints internos), protegidas con Resilience4j
(instancia "products"). Todas son idempotentes por numero de pedido en products, asi que el retry es seguro.
Los 4xx (sin stock, sin precio, no disponible) no se reintentan y llegan a OrderService
como HttpClientErrorException; el resto (red, 5xx, circuito abierto) como otra RuntimeException.
 */
@Component
public class ProductsClient {

    private static final String SERVICE_NAME = "sales";

    @Value("${products.url}")
    private String productsUrl;

    @Autowired
    private InternalTokenService internalTokenService;

    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "products")
    @Retry(name = "products")
    public ReservationDto reserve(ReservationRequestDto request) {
        return restTemplate.exchange(
                productsUrl + "api/internal/products/reservations",
                HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders()),
                ReservationDto.class
        ).getBody();
    }

    @CircuitBreaker(name = "products")
    @Retry(name = "products")
    public void release(String reference) {
        restTemplate.exchange(
                productsUrl + "api/internal/products/reservations/" + reference + "/release",
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Void.class
        );
    }

    @CircuitBreaker(name = "products")
    @Retry(name = "products")
    public void markSold(String reference, SoldItemsDto items) {
        post("api/internal/products/reservations/" + reference + "/sold", items);
    }

    @CircuitBreaker(name = "products")
    @Retry(name = "products")
    public void unmarkSold(String reference, SoldItemsDto items) {
        post("api/internal/products/reservations/" + reference + "/unsold", items);
    }

    private void post(String path, Object body) {
        restTemplate.exchange(productsUrl + path, HttpMethod.POST, new HttpEntity<>(body, internalHeaders()), Void.class);
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.INTERNAL_REQUEST, Boolean.toString(true));
        headers.set(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME));
        headers.set(Headers.FORWARDED_HOST, TenantContext.getCurrentTenant());
        return headers;
    }
}
