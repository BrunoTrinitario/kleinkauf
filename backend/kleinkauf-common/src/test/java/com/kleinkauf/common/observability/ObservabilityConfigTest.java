package com.kleinkauf.common.observability;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.observability.servlet.LogContextFilter;
import com.kleinkauf.common.observability.servlet.TenantServerRequestObservationConvention;
import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigTest {

    private final ObservabilityEnvironmentPostProcessor processor = new ObservabilityEnvironmentPostProcessor();

    private MockEnvironment process(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, null);
        return environment;
    }

    @Test
    void elPuertoDeGestionEsElDelServicioMas6000() {
        MockEnvironment environment = process(new MockEnvironment().withProperty("server.port", "3002"));

        assertThat(environment.getProperty("management.server.port")).isEqualTo("9002");
        assertThat(environment.getProperty("eureka.instance.metadata-map.management.port")).isEqualTo("9002");
        assertThat(environment.getProperty("management.endpoint.health.group.liveness.additional-path"))
                .isEqualTo("management:/health");
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.additional-path"))
                .isEqualTo("management:/ready");
        assertThat(environment.getProperty("management.endpoints.web.path-mapping.prometheus")).isEqualTo("metrics");
    }

    @Test
    void loQueDefineElServicioGana() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty("server.port", "8761")
                .withProperty("management.server.port", "9761")
                .withProperty("management.tracing.sampling.probability", "0.1"));

        assertThat(environment.getProperty("management.server.port")).isEqualTo("9761");
        assertThat(environment.getProperty("eureka.instance.metadata-map.management.port")).isEqualTo("9761");
        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
    }

    @Test
    void lasTrazasNoSeExportanSalvoQueSePrendan() {
        MockEnvironment environment = process(new MockEnvironment().withProperty("server.port", "3002"));

        assertThat(environment.getProperty("management.otlp.tracing.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.otlp.tracing.endpoint")).isEqualTo("http://localhost:4318/v1/traces");

        MockEnvironment withStack = process(new MockEnvironment()
                .withProperty("management.otlp.tracing.export.enabled", "true")
                .withProperty("management.otlp.tracing.endpoint", "http://alloy:4318/v1/traces"));

        assertThat(withStack.getProperty("management.otlp.tracing.export.enabled")).isEqualTo("true");
        assertThat(withStack.getProperty("management.otlp.tracing.endpoint")).isEqualTo("http://alloy:4318/v1/traces");
    }

    @Test
    void conPuertoAleatorioLaGestionTambienEsAleatoria() {
        MockEnvironment environment = process(new MockEnvironment().withProperty("server.port", "0"));

        assertThat(environment.getProperty("management.server.port")).isEqualTo("0");
    }

    @Test
    void sinPuertoDelServicioNoSeDefine() {
        MockEnvironment environment = process(new MockEnvironment());

        assertThat(environment.containsProperty("management.server.port")).isFalse();
        assertThat(environment.getProperty("management.endpoints.web.base-path")).isEqualTo("/");
    }

    @Test
    void laEtiquetaTenantSoloAceptaSchemas() {
        assertThat(TenantTag.of("mi_tienda")).isEqualTo("mi_tienda");
        assertThat(TenantTag.of(null)).isEqualTo(TenantTag.NONE);
        assertThat(TenantTag.of(" ")).isEqualTo(TenantTag.NONE);
        assertThat(TenantTag.of("Mi Tienda")).isEqualTo(TenantTag.INVALID);
        assertThat(TenantTag.of("x".repeat(64))).isEqualTo(TenantTag.INVALID);
        assertThat(TenantTag.of("a;drop")).isEqualTo(TenantTag.INVALID);
    }

    @Test
    void lasMetricasHttpLlevanElTenantDelHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(Headers.FORWARDED_HOST, "mi_tienda");
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, new MockHttpServletResponse());

        TenantServerRequestObservationConvention convention = new TenantServerRequestObservationConvention();

        assertThat(convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("tenant", "mi_tienda"));
        assertThat(convention.getLowCardinalityKeyValues(
                new ServerRequestObservationContext(new MockHttpServletRequest(), new MockHttpServletResponse())))
                .contains(KeyValue.of("tenant", "none"));
    }

    @Test
    void losLogsTienenElTenantYElUsuarioDuranteLaRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(Headers.FORWARDED_HOST, "mi_tienda");
        request.addHeader(Headers.USER_ID, "7");
        Map<String, String> seen = new HashMap<>();

        new LogContextFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            seen.put("tenant", MDC.get("tenant"));
            seen.put("userId", MDC.get("userId"));
        });

        assertThat(seen).containsEntry("tenant", "mi_tienda").containsEntry("userId", "7");
        assertThat(MDC.get("tenant")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }
}
