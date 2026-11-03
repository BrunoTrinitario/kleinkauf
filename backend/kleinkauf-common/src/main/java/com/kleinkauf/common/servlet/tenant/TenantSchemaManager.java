package com.kleinkauf.common.servlet.tenant;

/*
Lo implementa cada microservicio (TenantDataSource) para crear y migrar
el schema de un tenant antes de usar su conexion.
 */
public interface TenantSchemaManager {

    void prepareSchema(String tenantIdentifier);
}
