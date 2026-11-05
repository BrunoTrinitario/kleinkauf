package com.kleinkauf.common.servlet.tenant;

import com.kleinkauf.common.constants.TenantConstants;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/*
Decides at runtime which tenant/db to query
 */
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = TenantConstants.PUBLIC;

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
