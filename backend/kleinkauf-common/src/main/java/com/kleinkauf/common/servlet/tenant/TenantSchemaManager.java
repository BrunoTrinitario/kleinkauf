package com.kleinkauf.common.servlet.tenant;

/*
Implemented by each microservice (TenantDataSource) to create and migrate
a tenant's schema before using its connection.
 */
public interface TenantSchemaManager {

    void prepareSchema(String tenantIdentifier);
}
