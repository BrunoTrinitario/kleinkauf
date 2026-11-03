package com.kleinkauf.observability;

import com.kleinkauf.observability.collector.InstanceMetrics;
import com.kleinkauf.observability.collector.PrometheusText;
import com.kleinkauf.observability.collector.PrometheusText.Sample;
import com.kleinkauf.observability.collector.Traffic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsParsingTest {

    // Recorte de lo que devuelve /metrics de un servicio (Micrometer, formato 0.0.4)
    static final String METRICS = """
            # HELP http_server_requests_seconds
            # TYPE http_server_requests_seconds histogram
            http_server_requests_seconds_bucket{application="products-microservice",method="GET",outcome="SUCCESS",status="200",tenant="demo",uri="/api/v1/products",le="0.05"} 80
            http_server_requests_seconds_bucket{application="products-microservice",method="GET",outcome="SUCCESS",status="200",tenant="demo",uri="/api/v1/products",le="0.1"} 95
            http_server_requests_seconds_bucket{application="products-microservice",method="GET",outcome="SUCCESS",status="200",tenant="demo",uri="/api/v1/products",le="+Inf"} 100
            http_server_requests_seconds_count{application="products-microservice",method="GET",outcome="SUCCESS",status="200",tenant="demo",uri="/api/v1/products"} 100
            http_server_requests_seconds_count{application="products-microservice",method="GET",outcome="SERVER_ERROR",status="500",tenant="demo",uri="/api/v1/products"} 4
            http_server_requests_seconds_count{application="products-microservice",method="POST",outcome="CLIENT_ERROR",status="429",tenant="none",uri="/api/v1/auth/login"} 2
            http_server_requests_seconds_count{application="products-microservice",method="GET",outcome="SUCCESS",status="200",tenant="none",uri="/metrics"} 999
            http_server_requests_seconds_bucket{application="products-microservice",method="GET",outcome="SUCCESS",status="200",tenant="none",uri="/metrics",le="+Inf"} 999
            jvm_memory_used_bytes{application="products-microservice",area="heap",id="G1 Eden Space"} 1.0E8
            jvm_memory_used_bytes{application="products-microservice",area="heap",id="G1 Old Gen"} 5.0E7
            jvm_memory_used_bytes{application="products-microservice",area="nonheap",id="Metaspace"} 9.0E7
            jvm_memory_max_bytes{application="products-microservice",area="heap",id="G1 Eden Space"} -1.0
            jvm_memory_max_bytes{application="products-microservice",area="heap",id="G1 Old Gen"} 4.0E9
            process_cpu_usage{application="products-microservice"} 0.25
            jvm_threads_live_threads{application="products-microservice"} 42.0
            hikaricp_connections_active{application="products-microservice",pool="HikariPool-1"} 3.0
            hikaricp_connections_max{application="products-microservice",pool="HikariPool-1"} 10.0
            resilience4j_circuitbreaker_state{application="products-microservice",name="users",state="open"} 1.0
            resilience4j_circuitbreaker_state{application="products-microservice",name="users",state="closed"} 0.0
            resilience4j_circuitbreaker_state{application="products-microservice",name="sales",state="open"} 0.0
            """;

    @Test
    void leeEtiquetasValoresYSaltaComentarios() {
        List<Sample> samples = PrometheusText.parse("""
                # TYPE x counter
                x_total{a="uno",b="con \\"comillas\\", y coma"} 3.5 1700000000000
                sin_etiquetas 7
                linea rota{a="x" 1
                gauge_inf{le="+Inf"} +Inf
                """);

        assertThat(samples).hasSize(3);
        assertThat(samples.get(0).labels()).containsEntry("a", "uno").containsEntry("b", "con \"comillas\", y coma");
        assertThat(samples.get(0).value()).isEqualTo(3.5);
        assertThat(samples.get(1).name()).isEqualTo("sin_etiquetas");
        assertThat(samples.get(2).value()).isInfinite();
    }

    @Test
    void tomaLasMetricasSinLasDelPuertoDeGestion() {
        InstanceMetrics metrics = InstanceMetrics.from(PrometheusText.parse(METRICS));

        assertThat(metrics.requests()).isEqualTo(106);
        assertThat(metrics.errors5xx()).isEqualTo(4);
        assertThat(metrics.errors4xx()).isEqualTo(2);
        assertThat(metrics.rateLimited()).isEqualTo(2);
        assertThat(metrics.buckets()).containsEntry(Double.POSITIVE_INFINITY, 100.0);
        assertThat(metrics.heapUsedBytes()).isEqualTo(1.5e8);
        assertThat(metrics.heapMaxBytes()).isEqualTo(4.0e9);
        assertThat(metrics.cpu()).isEqualTo(0.25);
        assertThat(metrics.threads()).isEqualTo(42);
        assertThat(metrics.dbConnectionsActive()).isEqualTo(3);
        assertThat(metrics.dbConnectionsMax()).isEqualTo(10);
        assertThat(metrics.openCircuitBreakers()).containsExactly("users");
    }

    @Test
    void lasTasasSalenDeLaDiferenciaEntreLecturas() {
        InstanceMetrics before = metrics(100, 2, Map.of(0.05, 80.0, 0.1, 95.0, Double.POSITIVE_INFINITY, 100.0));
        InstanceMetrics after = metrics(200, 12, Map.of(0.05, 130.0, 0.1, 190.0, Double.POSITIVE_INFINITY, 200.0));

        Traffic.Rates rates = Traffic.rates(List.of(Traffic.delta(before, after)), 10);

        assertThat(rates.requestsPerSecond()).isEqualTo(10);
        assertThat(rates.errors5xxPerSecond()).isEqualTo(1);
        // 100 requests en el intervalo: 50 hasta 50 ms, 45 entre 50 y 100 ms, 5 mas lentas
        assertThat(rates.p50Ms()).isCloseTo(50.0, within(0.1));
        assertThat(rates.p95Ms()).isCloseTo(100.0, within(0.1));
        // El p99 cae en el bucket +Inf: lo mas que se sabe es el limite anterior
        assertThat(rates.p99Ms()).isCloseTo(100.0, within(0.1));
    }

    @Test
    void siLaInstanciaSeReinicioSeTomaLaLecturaNueva() {
        InstanceMetrics before = metrics(500, 0, Map.of(Double.POSITIVE_INFINITY, 500.0));
        InstanceMetrics after = metrics(30, 0, Map.of(Double.POSITIVE_INFINITY, 30.0));

        Traffic.Delta delta = Traffic.delta(before, after);

        assertThat(delta.requests()).isEqualTo(30);
        assertThat(delta.buckets()).containsEntry(Double.POSITIVE_INFINITY, 30.0);
    }

    @Test
    void sinRequestsNoHayPercentiles() {
        InstanceMetrics same = metrics(100, 0, Map.of(0.1, 100.0, Double.POSITIVE_INFINITY, 100.0));

        Traffic.Rates rates = Traffic.rates(List.of(Traffic.delta(same, same)), 10);

        assertThat(rates.requestsPerSecond()).isZero();
        assertThat(rates.p95Ms()).isNull();
    }

    static InstanceMetrics metrics(double requests, double errors5xx, Map<Double, Double> buckets) {
        return new InstanceMetrics(requests, errors5xx, 0, 0, new TreeMap<>(buckets),
                null, null, null, null, null, null, List.of(), Map.of());
    }

    static InstanceMetrics tenantMetrics(Map<String, InstanceMetrics.TenantRequests> tenants) {
        return new InstanceMetrics(0, 0, 0, 0, new TreeMap<>(),
                null, null, null, null, null, null, List.of(), tenants);
    }

    @Test
    void cuentaRequestsYErroresPorTienda_sinNoneNiInvalid() {
        InstanceMetrics metrics = InstanceMetrics.from(PrometheusText.parse(METRICS + """
                http_server_requests_seconds_count{method="GET",status="404",tenant="demo",uri="/x"} 6
                http_server_requests_seconds_count{method="GET",status="200",tenant="invalid",uri="/x"} 9
                """));

        // demo: 100 ok + 4 de 500 + 6 de 404; "none" (login y /metrics) e "invalid" no son tiendas
        assertThat(metrics.tenants()).containsOnlyKeys("demo");
        assertThat(metrics.tenants().get("demo")).isEqualTo(new InstanceMetrics.TenantRequests(110, 6, 4));
    }

    @Test
    void tasasPorTienda_sumanLasInstanciasYUnReinicioNoDaNegativo() {
        InstanceMetrics before = tenantMetrics(Map.of("demo", new InstanceMetrics.TenantRequests(100, 10, 0)));
        InstanceMetrics after = tenantMetrics(Map.of("demo", new InstanceMetrics.TenantRequests(150, 20, 0),
                "otra", new InstanceMetrics.TenantRequests(30, 0, 3)));
        // Otra instancia que se reinicio: el contador bajo, se toma la lectura nueva entera
        InstanceMetrics restartedBefore = tenantMetrics(Map.of("demo", new InstanceMetrics.TenantRequests(500, 0, 0)));
        InstanceMetrics restartedAfter = tenantMetrics(Map.of("demo", new InstanceMetrics.TenantRequests(50, 0, 0)));

        Map<String, Traffic.TenantRates> rates = Traffic.tenantRates(
                List.of(Traffic.delta(before, after), Traffic.delta(restartedBefore, restartedAfter)), 10);

        assertThat(rates.get("demo")).isEqualTo(new Traffic.TenantRates(10.0, 1.0, 0.0));
        assertThat(rates.get("otra")).isEqualTo(new Traffic.TenantRates(3.0, 0.0, 0.3));
    }
}
