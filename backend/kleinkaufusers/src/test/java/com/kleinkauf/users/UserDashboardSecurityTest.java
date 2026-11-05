package com.kleinkauf.users;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.security.InternalTokenService;
import com.kleinkauf.common.security.PermissionArea;
import com.kleinkauf.common.servlet.exceptions.GlobalExceptionHandler;
import com.kleinkauf.common.servlet.tenant.TenantFilter;
import com.kleinkauf.users.controllers.UserController;
import com.kleinkauf.users.dtos.dashboard.UserDashboardDto;
import com.kleinkauf.users.dtos.platform.RegistrationsDto;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.security.SecurityConfig;
import com.kleinkauf.users.services.RolePermissionService;
import com.kleinkauf.users.services.UserDashboardService;
import com.kleinkauf.users.services.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The store users dashboard is ADMINISTRADOR-only: an employee cannot get in even with every area
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, UserDashboardSecurityTest.TenantConfig.class, GlobalExceptionHandler.class})
class UserDashboardSecurityTest {

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

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserDashboardService userDashboardService;

	@BeforeEach
	void roles() throws Exception {
		when(roleRepository.findByUser_Id(ADMINISTRADOR)).thenReturn(List.of(role("ADMINISTRADOR")));
		when(roleRepository.findByUser_Id(EMPLEADO)).thenReturn(List.of(role("EMPLEADO")));
		when(rolePermissionService.maskFor("EMPLEADO")).thenReturn(PermissionArea.ALL);
		when(userDashboardService.dashboard(any(), any(), any())).thenReturn(new UserDashboardDto(3, 1, 30,
				Map.of("ADMINISTRADOR", 1L), new RegistrationsDto(LocalDate.now(), LocalDate.now(), "DAY", List.of())));
	}

	@Test
	void usersDashboard_administratorOnly() throws Exception {
		mockMvc.perform(get("/api/v1/users/dashboard")).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/users/dashboard"))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMINISTRADOR, get("/api/v1/users/dashboard")
						.param("from", "2026-10-01").param("to", "2026-10-31").param("groupBy", "WEEK")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3));

		verify(userDashboardService).dashboard("2026-10-01", "2026-10-31", "WEEK");
	}

	private static MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
		return request.header(Headers.USER_ID, userId);
	}

	private static Role role(String type) {
		Role role = new Role();
		role.setType(type);
		return role;
	}
}
