package com.kleinkauf.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/*
/health, /ready and /metrics go through the management port, without tenant or login (read by Docker,
Prometheus and observability). They do not exist on the gateway's public port.
Tests turn Prometheus off except with @AutoConfigureObservability.
 */
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class ManagementPortTest {

    @LocalManagementPort
    int managementPort;

    @LocalServerPort
    int serverPort;

    private WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthResponds200WithoutTenantOrLogin() {
        client(managementPort).get().uri("/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void readyRespondsWithDetail() {
        // Redis is not available in the tests: /ready may give 503, but it responds with the status and not 401 / 404
        client(managementPort).get().uri("/ready").exchange()
                .expectBody().jsonPath("$.status").exists();
    }

    @Test
    void metricsInPrometheusFormat() {
        client(managementPort).get().uri("/metrics").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("jvm_memory_used_bytes")
                        .contains("application=\"gateway-microservice\""));
    }

    @Test
    void publicPortDoesNotExposeManagement() {
        client(serverPort).get().uri("/metrics").header("Origin", "http://demo.localhost:2999").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(200));
    }
}
