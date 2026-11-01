package com.decor.common.servlet.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/*
Define en tiempo de ejecucion a que tenant/db debe consultar
 */
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
