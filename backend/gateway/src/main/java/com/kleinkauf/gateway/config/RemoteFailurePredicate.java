package com.kleinkauf.gateway.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Predicate;

/*
Decide que errores cuentan como falla del servicio remoto para Resilience4j.
Un 4xx es un error del pedido (ej: tenant inexistente), no que users este caido,
asi que no abre el circuito ni se reintenta. Tampoco se reintenta con el circuito abierto.
 */
public class RemoteFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable ex) {
        if (ex instanceof CallNotPermittedException) {
            return false;
        }
        return !(ex instanceof WebClientResponseException e && e.getStatusCode().is4xxClientError());
    }
}
