package com.kleinkauf.gateway.config;

import com.kleinkauf.common.observability.TenantTag;
import com.kleinkauf.gateway.constants.PermittedUrls;
import com.kleinkauf.gateway.security.filters.TenantGatewayFilter;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;

/*
"tenant" tag on the gateway's HTTP metrics (http_server_requests), with the schema resolved by
TenantGatewayFilter. If it was not resolved (platform, images, unknown tenant): "none".
 */
@Configuration
public class TenantObservationConfig {

    @Bean
    public ServerRequestObservationConvention tenantServerRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
                Object tenant = context.getAttributes().get(TenantGatewayFilter.TENANT_ATTRIBUTE);
                KeyValues values = super.getLowCardinalityKeyValues(context)
                        .and(KeyValue.of(TenantTag.KEY, TenantTag.of(tenant == null ? null : tenant.toString())));
                // The WS lasts as long as the connection: with its own uri it can be left out of the latencies
                if (PermittedUrls.WS_PATH.equals(context.getCarrier().getPath().value())) {
                    values = values.and(KeyValue.of("uri", PermittedUrls.WS_PATH));
                }
                return values;
            }
        };
    }
}
