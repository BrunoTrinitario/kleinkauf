package com.decor.common.servlet.autoconfigure;

import com.decor.common.constants.Headers;
import com.decor.common.servlet.exceptions.GlobalExceptionHandler;
import com.decor.common.servlet.tenant.DatabaseMultiTenantConnectionProvider;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.common.servlet.tenant.TenantFilter;
import com.decor.common.servlet.tenant.TenantIdentifierResolver;
import com.decor.common.servlet.tenant.TenantSchemaManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecorServletAutoConfigurationTest {

    @Configuration
    @AutoConfigurationPackage
    static class AppConfig {
    }

    // H2 en memoria en modo PostgreSQL para que Hibernate arranque de verdad
    // y @AutoConfigurationPackage simula el paquete de la @SpringBootApplication
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DecorServletAutoConfiguration.class))
            .withUserConfiguration(AppConfig.class)
            .withBean(DataSource.class, () -> new DriverManagerDataSource(
                    "jdbc:h2:mem:decor;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"))
            .withBean(TenantSchemaManager.class, () -> mock(TenantSchemaManager.class));

    @Test
    void creaLosBeansComunes() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenantFilter.class);
            assertThat(context).hasSingleBean(TenantIdentifierResolver.class);
            assertThat(context).hasSingleBean(DatabaseMultiTenantConnectionProvider.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(EntityManagerFactory.class);
        });
    }

    @Test
    void laConexionSePreparaYApuntaAlSchemaDelTenant() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        TenantSchemaManager schemaManager = mock(TenantSchemaManager.class);
        when(dataSource.getConnection()).thenReturn(connection);

        new DatabaseMultiTenantConnectionProvider(dataSource, schemaManager).getConnection("tienda1");

        verify(schemaManager).prepareSchema("tienda1");
        verify(connection).setSchema("tienda1");
    }

    @Test
    void tenantObligatorioSinHeaderResponde404() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TenantFilter(true).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void tenantOpcionalSinHeaderSigue() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TenantFilter(false).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void elTenantDelHeaderQuedaEnElContextoYSeLimpia() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Headers.FORWARDED_HOST, "tienda1");
        AtomicReference<String> visto = new AtomicReference<>();

        new TenantFilter(true).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> visto.set(TenantContext.getCurrentTenant()));

        assertThat(visto.get()).isEqualTo("tienda1");
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
