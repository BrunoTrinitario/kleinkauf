package com.kleinkauf.users;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.security.InternalTokenService;
import com.kleinkauf.common.servlet.exceptions.GlobalExceptionHandler;
import com.kleinkauf.common.servlet.tenant.TenantFilter;
import com.kleinkauf.users.controllers.RolePermissionController;
import com.kleinkauf.users.dtos.RolePermissionDto;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.security.SecurityConfig;
import com.kleinkauf.users.services.RolePermissionService;
import com.kleinkauf.users.services.UserService;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Configurar permisos es solo del ADMINISTRADOR (USUARIOS no se delega)
@WebMvcTest(controllers = RolePermissionController.class)
// GlobalExceptionHandler viene de la autoconfiguracion de kleinkauf-common, que no entra en @WebMvcTest
@Import({SecurityConfig.class, RolePermissionSecurityTest.TenantConfig.class, GlobalExceptionHandler.class})
class RolePermissionSecurityTest {

	private static final long ADMINISTRADOR = 1L;
	private static final long EMPLEADO = 2L;

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
	private RolePermissionService rolePermissionService;

	// Lo pide el CommandLineRunner de KleinKaufusersApplication
	@MockitoBean
	private UserService userService;

	@BeforeEach
	void roles() {
		when(roleRepository.findByUser_Id(ADMINISTRADOR)).thenReturn(List.of(role("ADMINISTRADOR")));
		when(roleRepository.findByUser_Id(EMPLEADO)).thenReturn(List.of(role("EMPLEADO")));
		when(rolePermissionService.list()).thenReturn(List.of(new RolePermissionDto("EMPLEADO", 4)));
	}

	@Test
	void verPermisos_soloAdministrador() throws Exception {
		mockMvc.perform(as(EMPLEADO, get("/api/v1/users/roles/permissions"))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMINISTRADOR, get("/api/v1/users/roles/permissions")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].permissions").value(4));
	}

	@Test
	void configurarPermisos_soloAdministrador() throws Exception {
		when(rolePermissionService.update(anyString(), anyInt())).thenReturn(new RolePermissionDto("EMPLEADO", 5));

		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/users/roles/EMPLEADO/permissions"), "{\"permissions\":5}")))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(ADMINISTRADOR, json(put("/api/v1/users/roles/EMPLEADO/permissions"), "{\"permissions\":5}")))
				.andExpect(status().isOk());
		verify(rolePermissionService).update("EMPLEADO", 5);
	}

	@Test
	void sinMascara_respondeBadRequest() throws Exception {
		mockMvc.perform(as(ADMINISTRADOR, json(put("/api/v1/users/roles/EMPLEADO/permissions"), "{}")))
				.andExpect(status().isBadRequest());
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
