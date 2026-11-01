package com.decor.product;

import com.decor.common.constants.Headers;
import com.decor.common.dto.RoleDto;
import com.decor.common.security.InternalTokenService;
import com.decor.common.servlet.tenant.TenantFilter;
import com.decor.product.controllers.BundleController;
import com.decor.product.controllers.ProductController;
import com.decor.product.controllers.StockController;
import com.decor.product.security.SecurityConfig;
import com.decor.product.services.BundleService;
import com.decor.product.services.ProductService;
import com.decor.product.services.StockService;
import com.decor.product.services.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Permisos por rol con el SecurityConfig y el HeaderAuthFilter reales.
Los roles salen de UserService (mock) segun el header X-User-Id que agrega el gateway.
 */
@WebMvcTest(controllers = {ProductController.class, StockController.class, BundleController.class})
@Import({SecurityConfig.class, SecurityRulesTest.TenantConfig.class})
class SecurityRulesTest {

	private static final long ADMIN = 1L;
	private static final long EMPLEADO = 2L;
	private static final long CLIENTE = 3L;

	@TestConfiguration
	static class TenantConfig {
		@Bean
		TenantFilter tenantFilter() {
			return new TenantFilter(true);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private InternalTokenService internalTokenService;

	@MockitoBean
	private ProductService productService;

	@MockitoBean
	private StockService stockService;

	@MockitoBean
	private BundleService bundleService;

	private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
		when(userService.getUserRolesById(ADMIN)).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 0)));
		when(userService.getUserRolesById(EMPLEADO)).thenReturn(List.of(new RoleDto("EMPLEADO", 0)));
		when(userService.getUserRolesById(CLIENTE)).thenReturn(List.of(new RoleDto("CLIENTE", 0)));
		request.header(Headers.FORWARDED_HOST, "tienda1");
		if (userId != null) request.header(Headers.USER_ID, userId);
		return request;
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void catalogoPublico_sinLogin() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/products/all"))).andExpect(status().isOk());
		mockMvc.perform(as(null, get("/api/v1/bundles/all"))).andExpect(status().isOk());
	}

	@Test
	void crearProducto_soloAdministrador() throws Exception {
		String body = "{\"name\":\"Silla\",\"stock\":1}";
		mockMvc.perform(as(CLIENTE, json(post("/api/v1/products"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/products"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(post("/api/v1/products"), body))).andExpect(status().isOk());
	}

	@Test
	void movimientosDeStock_administradorYEmpleado() throws Exception {
		String body = "{\"type\":\"ENTRADA\",\"quantity\":3}";
		mockMvc.perform(as(null, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(CLIENTE, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isCreated());
		mockMvc.perform(as(ADMIN, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isCreated());

		mockMvc.perform(as(CLIENTE, get("/api/v1/products/5/stock/movements"))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/5/stock/movements"))).andExpect(status().isOk());
	}

	@Test
	void stockBajo_administradorYEmpleado() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/products/stock/low"))).andExpect(status().isForbidden());
		mockMvc.perform(as(CLIENTE, get("/api/v1/products/stock/low"))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/stock/low"))).andExpect(status().isOk());
	}

	@Test
	void costoYMargen_soloAdministrador() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/products/5/pricing"))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/5/pricing"))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, get("/api/v1/products/5/pricing"))).andExpect(status().isOk());

		String body = "{\"price\":100,\"cost\":60}";
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/products/5/pricing"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/products/5/pricing"), body))).andExpect(status().isOk());
	}

	@Test
	void precioDelCombo_soloAdministrador() throws Exception {
		String body = "{\"price\":90000}";
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/bundles/1/price"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/bundles/1/price"), body))).andExpect(status().isOk());
	}
}
