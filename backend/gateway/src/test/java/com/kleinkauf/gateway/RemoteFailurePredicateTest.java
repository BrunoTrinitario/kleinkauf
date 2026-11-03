package com.kleinkauf.gateway;

import com.kleinkauf.gateway.config.RemoteFailurePredicate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteFailurePredicateTest {

    private final RemoteFailurePredicate predicate = new RemoteFailurePredicate();

    @Test
    void erroresDeRedYServidorCuentanComoFalla() {
        assertTrue(predicate.test(new IOException("connection refused")));
        assertTrue(predicate.test(new TimeoutException()));
        assertTrue(predicate.test(WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
    }

    @Test
    void errores4xxNoCuentanComoFalla() {
        assertFalse(predicate.test(WebClientResponseException.create(404, "Not Found", null, null, null)));
        assertFalse(predicate.test(WebClientResponseException.create(400, "Bad Request", null, null, null)));
    }

    @Test
    void circuitoAbiertoNoSeReintenta() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("users");
        circuitBreaker.transitionToForcedOpenState();

        assertFalse(predicate.test(CallNotPermittedException.createCallNotPermittedException(circuitBreaker)));
    }
}
