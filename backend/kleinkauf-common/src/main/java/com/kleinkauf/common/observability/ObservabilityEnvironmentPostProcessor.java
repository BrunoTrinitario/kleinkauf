package com.kleinkauf.common.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Actuator configuration shared by all services. These are defaults: if a service
defines the same property in its application.properties, the service's value wins.
- Internal management port (server.port + 6000, e.g. products 3002 -> 9002). The gateway does not expose it:
  it is read by Docker, Prometheus and observability.
- /health: liveness (the process is alive). /ready: readiness (database, Redis, Eureka...).
  /status: full health detail. /metrics: metrics in Prometheus format.
- The management port is published in the Eureka metadata (management.port) so observability
  can find each instance.
- traceId, spanId and tenant in the logs. Plain text by default; JSON (logstash) with the observability
  stack, which asks for it with LOGGING_STRUCTURED_FORMAT_CONSOLE so Loki reads them by field.
- Traces over OTLP, off unless the observability stack is up.
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final int MANAGEMENT_PORT_OFFSET = 6000;
    static final String SOURCE_NAME = "kleinkaufObservabilityDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();

        if (!environment.containsProperty("management.server.port")) {
            Integer serverPort = environment.getProperty("server.port", Integer.class);
            if (serverPort != null) {
                // Random port (tests): management also on a random one, separate from the app's
                defaults.put("management.server.port", serverPort == 0 ? 0 : serverPort + MANAGEMENT_PORT_OFFSET);
            }
        }

        defaults.put("management.endpoints.web.base-path", "/");
        defaults.put("management.endpoints.web.exposure.include", "health,prometheus");
        defaults.put("management.endpoints.web.path-mapping.prometheus", "metrics");
        defaults.put("management.endpoints.web.path-mapping.health", "status");
        defaults.put("management.endpoint.health.show-details", "always");
        defaults.put("management.endpoint.health.show-components", "always");
        defaults.put("management.endpoint.health.probes.enabled", "true");
        defaults.put("management.endpoint.health.group.liveness.include", "livenessState,ping");
        defaults.put("management.endpoint.health.group.liveness.additional-path", "management:/health");
        defaults.put("management.endpoint.health.group.readiness.include", "*");
        defaults.put("management.endpoint.health.group.readiness.exclude", "livenessState");
        defaults.put("management.endpoint.health.group.readiness.additional-path", "management:/ready");
        defaults.put("management.endpoint.health.group.readiness.show-details", "always");
        defaults.put("management.endpoint.health.group.readiness.show-components", "always");
        defaults.put("eureka.instance.metadata-map.management.port", "${management.server.port:}");

        // Metrics: service name on all of them and an HTTP latency histogram (p50 / p95 / p99)
        defaults.put("management.metrics.tags.application", "${spring.application.name:}");
        defaults.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");

        // Traces: all of them in development. traceId in the logs, also in the (reactive) gateway
        defaults.put("management.tracing.sampling.probability", "1.0");
        defaults.put("spring.reactor.context-propagation", "auto");
        defaults.put("logging.pattern.correlation", "[${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{tenant:-}] ");

        // Traces are sent over OTLP to the collector (Alloy, which passes them to Tempo). Off by default so that without
        // the observability stack there are no connection errors: docker-compose.observability.yml turns it on
        defaults.put("management.otlp.tracing.export.enabled", "false");
        defaults.put("management.otlp.tracing.endpoint", "http://localhost:4318/v1/traces");

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }

    // After reading application.properties, so it sees the service's server.port
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
