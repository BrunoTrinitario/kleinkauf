package com.kleinkauf.common.servlet.autoconfigure;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.servlet.exceptions.GlobalExceptionHandler;
import com.kleinkauf.common.servlet.tenant.DatabaseMultiTenantConnectionProvider;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.common.servlet.tenant.TenantFilter;
import com.kleinkauf.common.servlet.tenant.TenantIdentifierResolver;
import com.kleinkauf.common.servlet.tenant.TenantSchemaManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KleinKaufServletAutoConfigurationTest {

    @Configuration
    @AutoConfigurationPackage
    static class AppConfig {
    }

    // In-memory H2 in PostgreSQL mode so Hibernate really starts
    // and @AutoConfigurationPackage simulates the @SpringBootApplication package
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KleinKaufServletAutoConfiguration.class))
            .withUserConfiguration(AppConfig.class)
            .withBean(DataSource.class, () -> new DriverManagerDataSource(
                    "jdbc:h2:mem:kleinkauf;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"))
            .withBean(TenantSchemaManager.class, () -> mock(TenantSchemaManager.class));

    @Test
    void createsTheSharedBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenantFilter.class);
            assertThat(context).hasSingleBean(TenantIdentifierResolver.class);
            assertThat(context).hasSingleBean(DatabaseMultiTenantConnectionProvider.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(EntityManagerFactory.class);
        });
    }

    // The gateway is reactive: it has the library on the classpath but must not activate anything servlet
    @Test
    void doesNotActivateInReactiveApp() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KleinKaufServletAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TenantFilter.class);
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean("entityManagerFactory");
                });
    }

    @Test
    void connectionIsPreparedAndPointsToTenantSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        TenantSchemaManager schemaManager = mock(TenantSchemaManager.class);
        when(dataSource.getConnection()).thenReturn(connection);

        new DatabaseMultiTenantConnectionProvider(dataSource, schemaManager).getConnection("tienda1");

        verify(schemaManager).prepareSchema("tienda1");
        verify(connection).setSchema("tienda1");
    }

    // Preparing the schema (in users, running the migration) is expensive: once per tenant, not per connection.
    // And it is prepared before taking the connection, so as not to hold one while waiting for others
    @Test
    void schemaIsPreparedOnlyOnceAndBeforeTakingTheConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        TenantSchemaManager schemaManager = mock(TenantSchemaManager.class);
        when(dataSource.getConnection()).thenReturn(connection);
        DatabaseMultiTenantConnectionProvider provider = new DatabaseMultiTenantConnectionProvider(dataSource, schemaManager);

        provider.getConnection("tienda1");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(schemaManager, dataSource);
        order.verify(schemaManager).prepareSchema("tienda1");
        order.verify(dataSource).getConnection();

        provider.getConnection("tienda1");
        provider.getConnection("tienda2");

        verify(schemaManager, org.mockito.Mockito.times(1)).prepareSchema("tienda1");
        verify(schemaManager, org.mockito.Mockito.times(1)).prepareSchema("tienda2");
    }

    // If preparing fails it is not marked: the next request tries again
    @Test
    void ifPreparingFailsItIsRetried() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));
        TenantSchemaManager schemaManager = mock(TenantSchemaManager.class);
        org.mockito.Mockito.doThrow(new RuntimeException("db caida")).doNothing()
                .when(schemaManager).prepareSchema("tienda1");
        DatabaseMultiTenantConnectionProvider provider = new DatabaseMultiTenantConnectionProvider(dataSource, schemaManager);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> provider.getConnection("tienda1"));
        provider.getConnection("tienda1");

        verify(schemaManager, org.mockito.Mockito.times(2)).prepareSchema("tienda1");
    }

    @Test
    void requiredTenantWithoutHeaderResponds404() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TenantFilter(true).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
    }

    // E.g. product images: the browser does not send Origin when loading an <img>, so the tenant does not arrive
    @Test
    void publicPathWithoutHeaderPassesEvenIfTenantIsRequired() throws Exception {
        TenantFilter filter = new TenantFilter(true, java.util.List.of("/products/images/"));

        MockFilterChain imageChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/products/images/abc.png"), new MockHttpServletResponse(), imageChain);
        assertThat(imageChain.getRequest()).isNotNull();

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain otherChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/products"), response, otherChain);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(otherChain.getRequest()).isNull();
    }

    // An empty prefix (e.g. a property without a value) cannot make every route public
    @Test
    void emptyPrefixDoesNotMakeRoutesPublic() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TenantFilter(true, java.util.List.of("", " ")).doFilter(
                new MockHttpServletRequest("GET", "/api/v1/products"), response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void optionalTenantWithoutHeaderPasses() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TenantFilter(false).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void headerTenantIsSetInContextAndCleared() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Headers.FORWARDED_HOST, "tienda1");
        AtomicReference<String> seen = new AtomicReference<>();

        new TenantFilter(true).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set(TenantContext.getCurrentTenant()));

        assertThat(seen.get()).isEqualTo("tienda1");
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
