package com.kleinkauf.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/*
/health, /ready y /metrics van por el puerto de gestion, sin tenant ni login (lo leen Docker,
Prometheus y observability). Por el puerto publico del gateway no existen.
Los tests apagan Prometheus salvo con @AutoConfigureObservability.
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
    void healthResponde200SinTenantNiLogin() {
        client(managementPort).get().uri("/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void readyRespondeConElDetalle() {
        // Redis no esta en los tests: /ready puede dar 503, pero responde el estado y no 401 / 404
        client(managementPort).get().uri("/ready").exchange()
                .expectBody().jsonPath("$.status").exists();
    }

    @Test
    void metricsEnFormatoPrometheus() {
        client(managementPort).get().uri("/metrics").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("jvm_memory_used_bytes")
                        .contains("application=\"gateway-microservice\""));
    }

    @Test
    void elPuertoPublicoNoExponeLaGestion() {
        client(serverPort).get().uri("/metrics").header("Origin", "http://demo.localhost:2999").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(200));
    }
}
