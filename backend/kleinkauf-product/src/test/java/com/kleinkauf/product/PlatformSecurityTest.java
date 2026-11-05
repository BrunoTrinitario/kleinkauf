package com.kleinkauf.product;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.security.InternalTokenService;
import com.kleinkauf.common.security.PermissionArea;
import com.kleinkauf.common.servlet.exceptions.GlobalExceptionHandler;
import com.kleinkauf.common.servlet.tenant.TenantFilter;
import com.kleinkauf.product.controllers.PlatformController;
import com.kleinkauf.product.controllers.ProductController;
import com.kleinkauf.product.security.SecurityConfig;
import com.kleinkauf.product.services.PlatformUsageService;
import com.kleinkauf.product.services.ProductService;
import com.kleinkauf.product.services.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
/api/v1/platform/** in products: no tenant (as in application.properties, kleinkauf.tenant.public-paths)
and SUPERADMIN only. The rest of the routes still require a tenant.
 */
@WebMvcTest(controllers = {PlatformController.class, ProductController.class})
// GlobalExceptionHandler turns the controller's ApiException into its status (404)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, PlatformSecurityTest.TenantConfig.class})
class PlatformSecurityTest {

	private static final long SUPERADMIN = 1L;
	private static final long ADMIN = 2L;
	private static final String USAGE = "/api/v1/platform/products/usage";

	@TestConfiguration
	static class TenantConfig {
		@Bean
		TenantFilter tenantFilter() {
			return new TenantFilter(true, List.of("/products/images/", "/api/v1/platform/"));
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private InternalTokenService internalTokenService;

	@MockitoBean
	private PlatformUsageService platformUsageService;

	@MockitoBean
	private ProductService productService;

	@BeforeEach
	void roles() {
		when(userService.getUserRolesById(SUPERADMIN)).thenReturn(List.of(new RoleDto("SUPERADMIN", 0)));
		when(userService.getUserRolesById(ADMIN)).thenReturn(List.of(new RoleDto("ADMINISTRADOR", PermissionArea.ALL)));
	}

	@Test
	void usagePerTenant_superadminOnlyAndWithoutTenant() throws Exception {
		mockMvc.perform(get(USAGE)).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, get(USAGE))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, get(USAGE))).andExpect(status().isOk());
	}

	@Test
	void usagePerTenant_withTenant_doesNotExist() throws Exception {
		mockMvc.perform(as(SUPERADMIN, get(USAGE)).header(Headers.FORWARDED_HOST, "tienda1"))
				.andExpect(status().isNotFound());
	}

	@Test
	void restStillWithoutTenant404() throws Exception {
		mockMvc.perform(as(ADMIN, get("/api/v1/products"))).andExpect(status().isNotFound());
	}

	private static MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
		return request.header(Headers.USER_ID, userId);
	}
}
