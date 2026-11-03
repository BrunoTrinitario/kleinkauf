package com.kleinkauf.observability.collector;

import com.kleinkauf.observability.collector.TargetDiscovery.Target;
import com.kleinkauf.observability.dto.ServiceHealthDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/*
Lee /ready y /metrics del puerto de gestion de una instancia.
/ready responde 503 con el detalle cuando algo esta caido: ese cuerpo tambien se lee.
 */
@Component
public class InstanceProbe {

    // Formato de texto 0.0.4 (sin pedirlo, Actuator podria contestar OpenMetrics)
    static final String PROMETHEUS_TEXT = "text/plain;version=0.0.4";

    public record Result(Target target, String status, Map<String, String> components, String error,
                         InstanceMetrics metrics) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    public InstanceProbe(@Qualifier("pollingRestTemplate") RestTemplate restTemplate, ObjectMapper mapper) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }

    public Result probe(Target target) {
        String status;
        Map<String, String> components = new LinkedHashMap<>();
        String error = null;
        try {
            JsonNode ready = readReady(target.baseUrl() + "/ready");
            status = ServiceHealthDto.UP.equals(ready.path("status").asText()) ? ServiceHealthDto.UP : ServiceHealthDto.DOWN;
            ready.path("components").properties().forEach(component ->
                    components.put(component.getKey(), component.getValue().path("status").asText("UNKNOWN")));
        } catch (Exception e) {
            status = ServiceHealthDto.DOWN;
            error = "Sin respuesta de /ready: " + e.getClass().getSimpleName();
        }

        InstanceMetrics metrics = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, PROMETHEUS_TEXT);
            String body = restTemplate.exchange(target.baseUrl() + "/metrics", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class).getBody();
            metrics = InstanceMetrics.from(PrometheusText.parse(body));
        } catch (Exception e) {
            if (error == null) {
                error = "Sin respuesta de /metrics: " + e.getClass().getSimpleName();
            }
        }
        return new Result(target, status, components, error, metrics);
    }

    private JsonNode readReady(String url) throws Exception {
        String body;
        try {
            body = restTemplate.getForObject(url, String.class);
        } catch (HttpStatusCodeException e) {
            body = e.getResponseBodyAsString();
        }
        return mapper.readTree(body == null || body.isBlank() ? "{}" : body);
    }
}
