package com.decor.gateway.config;

import com.decor.common.observability.TenantTag;
import com.decor.gateway.security.filters.TenantGatewayFilter;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;

/*
Etiqueta "tenant" en las metricas HTTP del gateway (http_server_requests), con el schema que resolvio
TenantGatewayFilter. Si no se resolvio (plataforma, imagenes, tenant inexistente): "none".
 */
@Configuration
public class TenantObservationConfig {

    @Bean
    public ServerRequestObservationConvention tenantServerRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
                Object tenant = context.getAttributes().get(TenantGatewayFilter.TENANT_ATTRIBUTE);
                return super.getLowCardinalityKeyValues(context)
                        .and(KeyValue.of(TenantTag.KEY, TenantTag.of(tenant == null ? null : tenant.toString())));
            }
        };
    }
}
