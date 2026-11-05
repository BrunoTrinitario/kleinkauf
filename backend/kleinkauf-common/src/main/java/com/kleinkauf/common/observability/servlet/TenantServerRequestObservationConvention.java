package com.kleinkauf.common.observability.servlet;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.observability.TenantTag;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/*
Adds the "tenant" tag to the HTTP metrics (http_server_requests) with the schema sent by the gateway.
It is read from the header and not from TenantContext because tags are computed when the request ends,
when TenantFilter has already cleared it.
 */
public class TenantServerRequestObservationConvention extends DefaultServerRequestObservationConvention {

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        String tenant = context.getCarrier().getHeader(Headers.FORWARDED_HOST);
        return super.getLowCardinalityKeyValues(context).and(KeyValue.of(TenantTag.KEY, TenantTag.of(tenant)));
    }
}
