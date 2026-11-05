package com.kleinkauf.observability.collector;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/*
Rates between two readings of the counters of one or more instances of a service.
- If a counter went down, the instance restarted: the whole new reading is taken.
- Percentiles come from the histogram of the requests between the two readings, with the same
  interpolation as Prometheus' histogram_quantile. Without requests in the interval there are no percentiles.
 */
public final class Traffic {

    public record Rates(double requestsPerSecond, double errors5xxPerSecond, double errors4xxPerSecond,
                        double rateLimitedPerSecond, Double p50Ms, Double p95Ms, Double p99Ms) {
    }

    // What happened on an instance between two readings
    public record Delta(double requests, double errors5xx, double errors4xx, double rateLimited,
                        Map<Double, Double> buckets, Map<String, InstanceMetrics.TenantRequests> tenants) {
    }

    // Requests and errors per second of a store
    public record TenantRates(double requestsPerSecond, double errors4xxPerSecond, double errors5xxPerSecond) {

        public static final TenantRates ZERO = new TenantRates(0, 0, 0);
    }

    private Traffic() {
    }

    public static Delta delta(InstanceMetrics previous, InstanceMetrics current) {
        Map<Double, Double> buckets = new TreeMap<>();
        current.buckets().forEach((le, count) ->
                buckets.put(le, increase(previous.buckets().getOrDefault(le, 0.0), count)));
        Map<String, InstanceMetrics.TenantRequests> tenants = new TreeMap<>();
        current.tenants().forEach((tenant, now) -> {
            InstanceMetrics.TenantRequests before = previous.tenants().getOrDefault(tenant, InstanceMetrics.TenantRequests.ZERO);
            tenants.put(tenant, new InstanceMetrics.TenantRequests(increase(before.requests(), now.requests()),
                    increase(before.errors4xx(), now.errors4xx()), increase(before.errors5xx(), now.errors5xx())));
        });
        return new Delta(
                increase(previous.requests(), current.requests()),
                increase(previous.errors5xx(), current.errors5xx()),
                increase(previous.errors4xx(), current.errors4xx()),
                increase(previous.rateLimited(), current.rateLimited()),
                buckets,
                tenants);
    }

    // Per store, adding up the instances. Stores without requests in the interval do not appear (they are 0)
    public static Map<String, TenantRates> tenantRates(List<Delta> deltas, double seconds) {
        Map<String, InstanceMetrics.TenantRequests> totals = new TreeMap<>();
        for (Delta delta : deltas) {
            delta.tenants().forEach((tenant, requests) -> totals.merge(tenant, requests, InstanceMetrics.TenantRequests::plus));
        }
        double s = seconds <= 0 ? 1 : seconds;
        Map<String, TenantRates> rates = new TreeMap<>();
        totals.forEach((tenant, t) -> rates.put(tenant,
                new TenantRates(round(t.requests() / s), round(t.errors4xx() / s), round(t.errors5xx() / s))));
        return rates;
    }

    public static Rates rates(List<Delta> deltas, double seconds) {
        double requests = 0;
        double errors5xx = 0;
        double errors4xx = 0;
        double rateLimited = 0;
        Map<Double, Double> buckets = new TreeMap<>();
        for (Delta delta : deltas) {
            requests += delta.requests();
            errors5xx += delta.errors5xx();
            errors4xx += delta.errors4xx();
            rateLimited += delta.rateLimited();
            delta.buckets().forEach((le, count) -> buckets.merge(le, count, Double::sum));
        }
        double s = seconds <= 0 ? 1 : seconds;
        return new Rates(requests / s, errors5xx / s, errors4xx / s, rateLimited / s,
                toMs(quantile(0.50, buckets)), toMs(quantile(0.95, buckets)), toMs(quantile(0.99, buckets)));
    }

    // cumulative buckets (as in Prometheus): le -> requests with latency <= le
    static Double quantile(double q, Map<Double, Double> buckets) {
        if (buckets.isEmpty()) {
            return null;
        }
        TreeMap<Double, Double> sorted = new TreeMap<>(buckets);
        double total = sorted.lastEntry().getValue();
        if (total <= 0 || !Double.isInfinite(sorted.lastKey())) {
            return null;
        }
        double rank = q * total;
        double previousLe = 0;
        double previousCount = 0;
        for (Map.Entry<Double, Double> bucket : sorted.entrySet()) {
            double le = bucket.getKey();
            double count = bucket.getValue();
            if (count >= rank) {
                if (Double.isInfinite(le)) {
                    // Falls in the last bucket: the most that can be said is the previous bound
                    return previousLe;
                }
                double inBucket = count - previousCount;
                if (inBucket <= 0) {
                    return le;
                }
                return previousLe + (le - previousLe) * (rank - previousCount) / inBucket;
            }
            previousLe = le;
            previousCount = count;
        }
        return previousLe;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static double increase(double previous, double current) {
        return current >= previous ? current - previous : current;
    }

    private static Double toMs(Double seconds) {
        return seconds == null ? null : Math.round(seconds * 10_000) / 10.0;
    }
}
