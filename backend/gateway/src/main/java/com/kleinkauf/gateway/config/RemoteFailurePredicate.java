package com.kleinkauf.gateway.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Predicate;

/*
Decides which errors count as a failure of the remote service for Resilience4j.
A 4xx is a request error (e.g. unknown tenant), not users being down,
so it does not open the circuit and is not retried. It is not retried with the circuit open either.
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
