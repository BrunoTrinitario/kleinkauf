package com.decor.common.observability.servlet;

import com.decor.common.constants.Headers;
import com.decor.common.observability.TenantTag;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/*
Suma la etiqueta "tenant" a las metricas HTTP (http_server_requests) con el schema que manda el gateway.
Se lee del header y no del TenantContext porque las etiquetas se calculan al terminar la request,
cuando el TenantFilter ya lo limpio.
 */
public class TenantServerRequestObservationConvention extends DefaultServerRequestObservationConvention {

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        String tenant = context.getCarrier().getHeader(Headers.FORWARDED_HOST);
        return super.getLowCardinalityKeyValues(context).and(KeyValue.of(TenantTag.KEY, TenantTag.of(tenant)));
    }
}
