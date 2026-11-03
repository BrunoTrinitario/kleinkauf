package com.decor.observability.collector;

import com.decor.observability.collector.InstanceProbe.Result;
import com.decor.observability.collector.TargetDiscovery.Target;
import com.decor.observability.dto.ConnectedUsersDto;
import com.decor.observability.dto.InstanceHealthDto;
import com.decor.observability.dto.PlatformOverviewDto;
import com.decor.observability.dto.ServiceHealthDto;
import com.decor.observability.dto.ServiceTrafficDto;
import com.decor.observability.dto.TrafficUpdateDto;
import com.decor.observability.store.StoreFeeds;
import com.decor.observability.ws.Channels;
import com.decor.observability.ws.SessionRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
Recolector (lo llama Schedules cada observability.poll.interval):
1. Lee /ready y /metrics de cada instancia, todas a la vez (un hilo virtual por instancia).
2. Arma la salud y el trafico por servicio. Las tasas salen de la lectura anterior de cada instancia.
3. Guarda la lectura (PlatformState) y la manda por el WS: platform.health solo si cambio algun estado,
   platform.traffic siempre.
4. Con el trafico del gateway por tienda (etiqueta tenant) actualiza los canales de las tiendas (StoreFeeds).
   Se usa solo el gateway: cada request de una tienda pasa una vez por ahi (en los servicios se contaria de nuevo).
 */
@Slf4j
@Component
public class Collector {

    private record Reading(Instant at, InstanceMetrics metrics) {
    }

    private final TargetDiscovery discovery;
    private final InstanceProbe probe;
    private final ConnectedUsers connectedUsers;
    private final PlatformState state;
    private final SessionRegistry sessions;
    private final StoreFeeds storeFeeds;
    private final Clock clock;
    private final Duration timeout;
    private final String gatewayService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // Ultima lectura de cada instancia (por id): de aca salen las tasas
    private final Map<String, Reading> previous = new HashMap<>();
    private String lastHealthSignature;

    public Collector(TargetDiscovery discovery, InstanceProbe probe, ConnectedUsers connectedUsers,
                     PlatformState state, SessionRegistry sessions, StoreFeeds storeFeeds, Clock clock,
                     @Value("${observability.poll.timeout:2s}") Duration timeout,
                     @Value("${observability.gateway-service:gateway-microservice}") String gatewayService) {
        this.discovery = discovery;
        this.probe = probe;
        this.connectedUsers = connectedUsers;
        this.state = state;
        this.sessions = sessions;
        this.storeFeeds = storeFeeds;
        this.clock = clock;
        this.timeout = timeout;
        this.gatewayService = gatewayService;
    }

    // synchronized: si una vuelta se demora no se pisa con la siguiente
    public synchronized PlatformOverviewDto collect() {
        Instant now = clock.instant();
        List<Target> targets = discovery.targets();
        Map<String, List<Result>> byService = probeAll(targets).stream()
                .collect(Collectors.groupingBy(r -> r.target().service(), LinkedHashMap::new, Collectors.toList()));

        List<ServiceHealthDto> health = new ArrayList<>();
        List<ServiceTrafficDto> traffic = new ArrayList<>();
        for (String service : discovery.services(targets)) {
            List<Result> results = byService.getOrDefault(service, List.of());
            health.add(health(service, results));
            traffic.add(traffic(service, results, now));
        }
        Map<String, Traffic.TenantRates> tenantRates = tenantRates(byService.getOrDefault(gatewayService, List.of()), now);
        remember(targets, byService.values(), now);

        ConnectedUsersDto connected = connectedUsers.count();
        PlatformOverviewDto overview = new PlatformOverviewDto(now, health, traffic, connected);
        state.update(overview);

        String signature = signature(health);
        if (!signature.equals(lastHealthSignature)) {
            lastHealthSignature = signature;
            sessions.publish(Channels.PLATFORM_HEALTH, health);
        }
        sessions.publish(Channels.PLATFORM_TRAFFIC, new TrafficUpdateDto(now, traffic, connected));
        try {
            storeFeeds.update(tenantRates);
        } catch (RuntimeException e) {
            // Los canales de las tiendas no frenan el panel de la plataforma
            log.warn("No se pudieron actualizar los canales de las tiendas: {}", e.toString());
        }
        return overview;
    }

    private List<Result> probeAll(List<Target> targets) {
        List<Future<Result>> futures = targets.stream().map(t -> executor.submit(() -> probe.probe(t))).toList();
        List<Result> results = new ArrayList<>();
        // Cada instancia tiene su timeout por llamada; esto es un tope por si alguna se cuelga igual
        long deadline = System.nanoTime() + timeout.multipliedBy(3).toNanos();
        for (int i = 0; i < futures.size(); i++) {
            Future<Result> future = futures.get(i);
            try {
                results.add(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            } catch (Exception e) {
                future.cancel(true);
                Target target = targets.get(i);
                results.add(new Result(target, ServiceHealthDto.DOWN, Map.of(), "Sin respuesta a tiempo", null));
            }
        }
        return results;
    }

    private static ServiceHealthDto health(String service, List<Result> results) {
        List<InstanceHealthDto> instances = results.stream()
                .map(r -> new InstanceHealthDto(r.target().instanceId(), r.status(), r.components(), r.error()))
                .toList();
        long up = instances.stream().filter(i -> ServiceHealthDto.UP.equals(i.status())).count();
        String status = up == 0 ? ServiceHealthDto.DOWN
                : up == instances.size() ? ServiceHealthDto.UP : ServiceHealthDto.DEGRADED;
        List<String> openCircuitBreakers = List.copyOf(results.stream()
                .filter(r -> r.metrics() != null)
                .flatMap(r -> r.metrics().openCircuitBreakers().stream())
                .collect(Collectors.toCollection(TreeSet::new)));
        return new ServiceHealthDto(service, status, instances, openCircuitBreakers);
    }

    private ServiceTrafficDto traffic(String service, List<Result> results, Instant now) {
        List<InstanceMetrics> metrics = results.stream().map(Result::metrics).filter(Objects::nonNull).toList();

        Deltas deltas = deltas(results, now);
        Traffic.Rates rates = deltas.list().isEmpty() ? null : Traffic.rates(deltas.list(), deltas.seconds());

        return new ServiceTrafficDto(
                service,
                results.size(),
                rates == null ? null : round(rates.requestsPerSecond()),
                rates == null ? null : round(rates.errors5xxPerSecond()),
                rates == null ? null : round(rates.errors4xxPerSecond()),
                rates == null ? null : round(rates.rateLimitedPerSecond()),
                rates == null ? null : rates.p50Ms(),
                rates == null ? null : rates.p95Ms(),
                rates == null ? null : rates.p99Ms(),
                sum(metrics, InstanceMetrics::heapUsedBytes),
                sum(metrics, InstanceMetrics::heapMaxBytes),
                average(metrics, InstanceMetrics::cpu),
                sum(metrics, InstanceMetrics::threads),
                sum(metrics, InstanceMetrics::dbConnectionsActive),
                sum(metrics, InstanceMetrics::dbConnectionsMax));
    }

    // Trafico por tienda del gateway; null si todavia no hay dos lecturas de ninguna de sus instancias
    private Map<String, Traffic.TenantRates> tenantRates(List<Result> gateway, Instant now) {
        Deltas deltas = deltas(gateway, now);
        return deltas.list().isEmpty() ? null : Traffic.tenantRates(deltas.list(), deltas.seconds());
    }

    // Lo que paso en cada instancia desde su lectura anterior; seconds es el promedio de los intervalos
    private record Deltas(List<Traffic.Delta> list, double seconds) {
    }

    private Deltas deltas(List<Result> results, Instant now) {
        List<Traffic.Delta> deltas = new ArrayList<>();
        double seconds = 0;
        for (Result result : results) {
            Reading before = previous.get(result.target().instanceId());
            if (result.metrics() == null || before == null) {
                continue;
            }
            deltas.add(Traffic.delta(before.metrics(), result.metrics()));
            seconds += Duration.between(before.at(), now).toMillis() / 1000.0;
        }
        return new Deltas(deltas, deltas.isEmpty() ? 0 : seconds / deltas.size());
    }

    // Se olvidan las instancias que ya no estan; las que no respondieron /metrics quedan como estaban
    private void remember(List<Target> targets, Collection<List<Result>> results, Instant now) {
        previous.keySet().retainAll(targets.stream().map(Target::instanceId).toList());
        results.stream().flatMap(List::stream)
                .filter(r -> r.metrics() != null)
                .forEach(r -> previous.put(r.target().instanceId(), new Reading(now, r.metrics())));
    }

    private static String signature(List<ServiceHealthDto> health) {
        return health.stream()
                .map(s -> s.name() + "=" + s.status() + s.instances().stream()
                        .map(i -> i.id() + ":" + i.status()).sorted().toList() + s.openCircuitBreakers())
                .collect(Collectors.joining(";"));
    }

    private static Double sum(List<InstanceMetrics> metrics, Function<InstanceMetrics, Double> value) {
        List<Double> values = metrics.stream().map(value).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static Double average(List<InstanceMetrics> metrics, Function<InstanceMetrics, Double> value) {
        List<Double> values = metrics.stream().map(value).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 1000) / 1000.0;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
