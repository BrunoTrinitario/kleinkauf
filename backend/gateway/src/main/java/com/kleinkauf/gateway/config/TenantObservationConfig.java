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
                KeyValues values = super.getLowCardinalityKeyValues(context)
                        .and(KeyValue.of(TenantTag.KEY, TenantTag.of(tenant == null ? null : tenant.toString())));
                // El WS dura lo que dura la conexion: con su propio uri se puede dejar afuera de las latencias
                if (PermittedUrls.WS_PATH.equals(context.getCarrier().getPath().value())) {
                    values = values.and(KeyValue.of("uri", PermittedUrls.WS_PATH));
                }
                return values;
            }
        };
    }
}
