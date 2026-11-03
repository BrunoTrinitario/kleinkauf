package com.kleinkauf.common.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Configuracion de Actuator igual en todos los servicios. Son valores por defecto: si un servicio
define la misma propiedad en su application.properties, gana la del servicio.
- Puerto de gestion interno (server.port + 6000, ej. products 3002 -> 9002). El gateway no lo expone:
  lo leen Docker, Prometheus y observability.
- /health: liveness (el proceso esta vivo). /ready: readiness (base, Redis, Eureka...).
  /status: el detalle completo de la salud. /metrics: metricas en formato Prometheus.
- El puerto de gestion se publica en la metadata de Eureka (management.port) para que observability
  encuentre a cada instancia.
- traceId, spanId y tenant en los logs. En texto por defecto; en JSON (logstash) con el stack de
  observabilidad, que lo pide con LOGGING_STRUCTURED_FORMAT_CONSOLE para que Loki los lea por campo.
- Trazas por OTLP, apagadas salvo con el stack de observabilidad levantado.
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
                // Puerto aleatorio (tests): la gestion tambien en uno aleatorio, separado del de la app
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

        // Metricas: nombre del servicio en todas y histograma de latencias HTTP (p50 / p95 / p99)
        defaults.put("management.metrics.tags.application", "${spring.application.name:}");
        defaults.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");

        // Trazas: todas en desarrollo. traceId en los logs, tambien en el gateway (reactivo)
        defaults.put("management.tracing.sampling.probability", "1.0");
        defaults.put("spring.reactor.context-propagation", "auto");
        defaults.put("logging.pattern.correlation", "[${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{tenant:-}] ");

        // Envio de trazas por OTLP al colector (Alloy, que las pasa a Tempo). Apagado por defecto para que sin
        // el stack de observabilidad no haya errores de conexion: lo prende docker-compose.observability.yml
        defaults.put("management.otlp.tracing.export.enabled", "false");
        defaults.put("management.otlp.tracing.endpoint", "http://localhost:4318/v1/traces");

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }

    // Despues de leer application.properties, asi ve el server.port del servicio
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
