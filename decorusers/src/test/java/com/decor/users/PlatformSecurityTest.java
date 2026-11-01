package com.decor.users;

import com.decor.common.constants.Headers;
import com.decor.common.security.InternalTokenService;
import com.decor.common.servlet.tenant.TenantFilter;
import com.decor.users.controllers.PlatformController;
import com.decor.users.entities.Role;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.security.SecurityConfig;
import com.decor.users.services.PlatformService;
import com.decor.users.services.TenantService;
import com.decor.users.services.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Permisos de /api/v1/platform/** con el SecurityConfig y el HeaderAuthFilter reales de users.
Los roles salen de RoleRepository (mock) segun el X-User-Id que agrega el gateway.
 */
@WebMvcTest(controllers = PlatformController.class)
@Import({SecurityConfig.class, PlatformSecurityTest.TenantConfig.class})
class PlatformSecurityTest {

	private static final long SUPERADMIN = 1L;
	private static final long ADMINISTRADOR = 2L;

	@TestConfiguration
	static class TenantConfig {
		@Bean
		TenantFilter tenantFilter() {
			return new TenantFilter(false);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RoleRepository roleRepository;

	@MockitoBean
	private InternalTokenService internalTokenService;

	@MockitoBean
	private PlatformService platformService;

	@MockitoBean
	private TenantService tenantService;

	// Lo pide el CommandLineRunner de DecorusersApplication
	@MockitoBean
	private UserService userService;

	@BeforeEach
	void roles() {
		when(roleRepository.findByUser_Id(SUPERADMIN)).thenReturn(List.of(role("SUPERADMIN")));
		when(roleRepository.findByUser_Id(ADMINISTRADOR)).thenReturn(List.of(role("ADMINISTRADOR")));
	}

	@Test
	void altaYLoginSonPublicos() throws Exception {
		mockMvc.perform(json(post("/api/v1/platform/tenants"),
						"{\"identifier\":\"mi-tienda\",\"ownerName\":\"Ana\",\"ownerEmail\":\"a@a.com\",\"ownerPassword\":\"x\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(json(post("/api/v1/platform/auth/login"), "{\"email\":\"root@decor.com\",\"password\":\"x\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void panelDeTenants_soloSuperadmin() throws Exception {
		mockMvc.perform(get("/api/v1/platform/admin/tenants")).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMINISTRADOR, get("/api/v1/platform/admin/tenants"))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, get("/api/v1/platform/admin/tenants"))).andExpect(status().isOk());
	}

	@Test
	void accionesDelSuperadmin() throws Exception {
		mockMvc.perform(as(ADMINISTRADOR, json(put("/api/v1/platform/admin/tenants/7/enabled"), "{\"enabled\":false}")))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, json(put("/api/v1/platform/admin/tenants/7/enabled"), "{\"enabled\":false}")))
				.andExpect(status().isOk());

		mockMvc.perform(as(ADMINISTRADOR, delete("/api/v1/platform/admin/tenants/7"))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, delete("/api/v1/platform/admin/tenants/7"))).andExpect(status().isNoContent());

		String body = "{\"identifier\":\"otra\",\"ownerName\":\"B\",\"ownerEmail\":\"b@b.com\",\"ownerPassword\":\"x\"}";
		mockMvc.perform(as(ADMINISTRADOR, json(post("/api/v1/platform/admin/tenants"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, json(post("/api/v1/platform/admin/tenants"), body))).andExpect(status().isCreated());
	}

	private static MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
		return request.header(Headers.USER_ID, userId);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static Role role(String type) {
		Role role = new Role();
		role.setType(type);
		return role;
	}
}
