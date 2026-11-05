package com.kleinkauf.observability.collector;

import com.kleinkauf.common.observability.TenantTag;
import com.kleinkauf.observability.collector.PrometheusText.Sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
What the collector takes from an instance's /metrics in one reading.
Request counters are cumulative since the instance started: rates come from subtracting
two readings (see Traffic). The rest are current values.
It does not count management port requests (made by Prometheus and observability) nor WS ones,
which last as long as the connection and would ruin the percentiles.
 */
public record InstanceMetrics(
        double requests,
        double errors5xx,
        double errors4xx,
        double rateLimited,
        // Cumulative latency histogram: bucket upper bound (seconds) -> count
        Map<Double, Double> buckets,
        Double heapUsedBytes,
        Double heapMaxBytes,
        Double cpu,
        Double threads,
        Double dbConnectionsActive,
        Double dbConnectionsMax,
        List<String> openCircuitBreakers,
        // Requests and errors per store (tenant tag), cumulative like the ones above. Without "none" or "invalid"
        Map<String, TenantRequests> tenants
) {

    public record TenantRequests(double requests, double errors4xx, double errors5xx) {

        static final TenantRequests ZERO = new TenantRequests(0, 0, 0);

        TenantRequests plus(TenantRequests other) {
            return new TenantRequests(requests + other.requests, errors4xx + other.errors4xx, errors5xx + other.errors5xx);
        }
    }

    static final String REQUESTS_COUNT = "http_server_requests_seconds_count";
    static final String REQUESTS_BUCKET = "http_server_requests_seconds_bucket";

    static final Set<String> IGNORED_URIS = Set.of("/metrics", "/health", "/ready", "/status", "/ws");

    public static InstanceMetrics from(List<Sample> samples) {
        double requests = 0;
        double errors5xx = 0;
        double errors4xx = 0;
        double rateLimited = 0;
        Map<Double, Double> buckets = new TreeMap<>();
        Double heapUsed = null;
        Double heapMax = null;
        Double cpu = null;
        Double threads = null;
        Double dbActive = null;
        Double dbMax = null;
        List<String> openCircuitBreakers = new ArrayList<>();
        Map<String, TenantRequests> tenants = new TreeMap<>();

        for (Sample sample : samples) {
            switch (sample.name()) {
                case REQUESTS_COUNT -> {
                    if (ignored(sample)) continue;
                    requests += sample.value();
                    String status = sample.label("status");
                    if (status != null && status.startsWith("5")) errors5xx += sample.value();
                    if (status != null && status.startsWith("4")) errors4xx += sample.value();
                    if ("429".equals(status)) rateLimited += sample.value();
                    String tenant = sample.label(TenantTag.KEY);
                    if (tenant != null && !TenantTag.NONE.equals(tenant) && !TenantTag.INVALID.equals(tenant)) {
                        boolean is5xx = status != null && status.startsWith("5");
                        boolean is4xx = status != null && status.startsWith("4");
                        tenants.merge(tenant, new TenantRequests(sample.value(), is4xx ? sample.value() : 0,
                                is5xx ? sample.value() : 0), TenantRequests::plus);
                    }
                }
                case REQUESTS_BUCKET -> {
                    if (ignored(sample)) continue;
                    Double le = PrometheusText.parseValue(String.valueOf(sample.label("le")));
                    if (le != null) buckets.merge(le, sample.value(), Double::sum);
                }
                case "jvm_memory_used_bytes" -> {
                    if ("heap".equals(sample.label("area"))) heapUsed = sum(heapUsed, sample.value());
                }
                case "jvm_memory_max_bytes" -> {
                    // -1: the pool has no maximum
                    if ("heap".equals(sample.label("area")) && sample.value() > 0) heapMax = sum(heapMax, sample.value());
                }
                case "process_cpu_usage" -> cpu = sample.value();
                case "jvm_threads_live_threads" -> threads = sample.value();
                case "hikaricp_connections_active" -> dbActive = sum(dbActive, sample.value());
                case "hikaricp_connections_max" -> dbMax = sum(dbMax, sample.value());
                case "resilience4j_circuitbreaker_state" -> {
                    if ("open".equals(sample.label("state")) && sample.value() >= 1 && sample.label("name") != null) {
                        openCircuitBreakers.add(sample.label("name"));
                    }
                }
                default -> {
                }
            }
        }
        return new InstanceMetrics(requests, errors5xx, errors4xx, rateLimited, buckets,
                heapUsed, heapMax, cpu, threads, dbActive, dbMax, List.copyOf(openCircuitBreakers), Map.copyOf(tenants));
    }

    private static boolean ignored(Sample sample) {
        String uri = sample.label("uri");
        return uri != null && IGNORED_URIS.contains(uri);
    }

    private static Double sum(Double current, double value) {
        return current == null ? value : current + value;
    }
}
