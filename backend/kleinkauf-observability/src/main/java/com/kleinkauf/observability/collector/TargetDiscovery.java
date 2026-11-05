package com.kleinkauf.observability.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
Who the collector reads:
- Each instance registered in eureka that publishes its management port (management.port metadata, set by
  kleinkauf-common): http://<ip>:<management port>.
- What is not registered in eureka (eureka itself): observability.static-targets=name=url,...
observability.services are the services that must be present: without instances they show as down.
 */
@Component
public class TargetDiscovery {

    static final String MANAGEMENT_PORT = "management.port";

    public record Target(String service, String instanceId, String baseUrl) {
    }

    private final DiscoveryClient discoveryClient;
    private final List<String> expectedServices;
    private final Map<String, String> staticTargets;

    public TargetDiscovery(DiscoveryClient discoveryClient,
                           @Value("${observability.services:}") List<String> expectedServices,
                           @Value("${observability.static-targets:}") List<String> staticTargets) {
        this.discoveryClient = discoveryClient;
        this.expectedServices = expectedServices.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.staticTargets = parseStatic(staticTargets);
    }

    public List<Target> targets() {
        List<Target> targets = new ArrayList<>();
        for (String service : discoveryClient.getServices()) {
            for (ServiceInstance instance : discoveryClient.getInstances(service)) {
                String port = instance.getMetadata().get(MANAGEMENT_PORT);
                if (port == null || port.isBlank()) {
                    continue;
                }
                targets.add(new Target(service, instanceId(instance),
                        "http://" + instance.getHost() + ":" + port.trim()));
            }
        }
        staticTargets.forEach((name, url) -> targets.add(new Target(name, name, url)));
        return targets;
    }

    // The required ones first, then the ones that showed up in eureka
    public List<String> services(List<Target> targets) {
        Set<String> names = new LinkedHashSet<>(expectedServices);
        names.addAll(staticTargets.keySet());
        targets.forEach(t -> names.add(t.service()));
        return List.copyOf(names);
    }

    private static String instanceId(ServiceInstance instance) {
        String id = instance.getInstanceId();
        return id != null ? id : instance.getHost() + ":" + instance.getPort();
    }

    private static Map<String, String> parseStatic(List<String> entries) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String url = entry.substring(eq + 1).trim();
            targets.put(entry.substring(0, eq).trim(), url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
        }
        return targets;
    }
}
