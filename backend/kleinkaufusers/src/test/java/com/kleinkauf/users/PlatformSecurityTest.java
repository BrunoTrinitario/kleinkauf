package com.kleinkauf.users;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.security.InternalTokenService;
import com.kleinkauf.common.servlet.tenant.TenantFilter;
import com.kleinkauf.users.controllers.BugReportController;
import com.kleinkauf.users.controllers.PlatformController;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.security.SecurityConfig;
import com.kleinkauf.users.services.BugReportService;
import com.kleinkauf.users.services.PlatformDashboardService;
import com.kleinkauf.users.services.PlatformService;
import com.kleinkauf.users.services.RolePermissionService;
import com.kleinkauf.users.services.SuperadminService;
import com.kleinkauf.users.services.TenantService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Permissions of /api/v1/platform/** with users' real SecurityConfig and HeaderAuthFilter.
Roles come from RoleRepository (mock) according to the X-User-Id added by the gateway.
 */
@WebMvcTest(controllers = {PlatformController.class, BugReportController.class})
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
	private RolePermissionService rolePermissionService;

	@MockitoBean
	private PlatformService platformService;

	@MockitoBean
	private TenantService tenantService;

	@MockitoBean
	private PlatformDashboardService platformDashboardService;

	@MockitoBean
	private SuperadminService superadminService;

	@MockitoBean
	private BugReportService bugReportService;

	// Required by KleinKaufusersApplication's CommandLineRunner
	@MockitoBean
	private UserService userService;

	@BeforeEach
	void roles() {
		when(roleRepository.findByUser_Id(SUPERADMIN)).thenReturn(List.of(role("SUPERADMIN")));
		when(roleRepository.findByUser_Id(ADMINISTRADOR)).thenReturn(List.of(role("ADMINISTRADOR")));
	}

	@Test
	void signUpAndLoginArePublic() throws Exception {
		mockMvc.perform(json(post("/api/v1/platform/tenants"),
						"{\"identifier\":\"mi-tienda\",\"ownerName\":\"Ana\",\"ownerEmail\":\"a@a.com\",\"ownerPassword\":\"x\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(json(post("/api/v1/platform/auth/login"), "{\"email\":\"root@kleinkauf.com\",\"password\":\"x\"}"))
				.andExpect(status().isOk());
	}

	// Reporting a bug: public, and with a session the controller receives the user from the gateway
	@Test
	void reportBug_isPublic() throws Exception {
		String body = "{\"description\":\"El carrito no suma bien\"}";
		mockMvc.perform(json(post("/api/v1/bug-reports"), body)).andExpect(status().isCreated());
		verify(bugReportService).create(any(), isNull(), isNull());

		mockMvc.perform(as(ADMINISTRADOR, json(post("/api/v1/bug-reports"), body))).andExpect(status().isCreated());
		verify(bugReportService).create(any(), eq(ADMINISTRADOR), isNull());
	}

	@Test
	void bugReports_superadminOnly() throws Exception {
		mockMvc.perform(get("/api/v1/platform/admin/bug-reports")).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMINISTRADOR, get("/api/v1/platform/admin/bug-reports"))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, get("/api/v1/platform/admin/bug-reports"))).andExpect(status().isOk());

		mockMvc.perform(as(ADMINISTRADOR, json(put("/api/v1/platform/admin/bug-reports/4/resolved"), "{\"resolved\":true}")))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, json(put("/api/v1/platform/admin/bug-reports/4/resolved"), "{\"resolved\":true}")))
				.andExpect(status().isOk());
	}

	@Test
	void tenantsDashboard_superadminOnly() throws Exception {
		mockMvc.perform(get("/api/v1/platform/admin/tenants")).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMINISTRADOR, get("/api/v1/platform/admin/tenants"))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, get("/api/v1/platform/admin/tenants"))).andExpect(status().isOk());
	}

	@Test
	void superadminActions() throws Exception {
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

	@Test
	void dashboardAndUsage_superadminOnly() throws Exception {
		for (String path : new String[]{"/api/v1/platform/admin/dashboard", "/api/v1/platform/admin/usage"}) {
			mockMvc.perform(get(path)).andExpect(status().isForbidden());
			mockMvc.perform(as(ADMINISTRADOR, get(path))).andExpect(status().isForbidden());
			mockMvc.perform(as(SUPERADMIN, get(path))).andExpect(status().isOk());
		}
	}

	@Test
	void superadminManagement_superadminOnly() throws Exception {
		mockMvc.perform(as(ADMINISTRADOR, get("/api/v1/platform/admin/superadmins"))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, get("/api/v1/platform/admin/superadmins"))).andExpect(status().isOk());

		String body = "{\"name\":\"Root\",\"email\":\"root2@kleinkauf.com\",\"password\":\"clave123\"}";
		mockMvc.perform(as(ADMINISTRADOR, json(post("/api/v1/platform/admin/superadmins"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, json(post("/api/v1/platform/admin/superadmins"), body))).andExpect(status().isCreated());

		mockMvc.perform(as(ADMINISTRADOR, json(put("/api/v1/platform/admin/superadmins/3/active"), "{\"active\":false}")))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(SUPERADMIN, json(put("/api/v1/platform/admin/superadmins/3/active"), "{\"active\":false}")))
				.andExpect(status().isOk());
		// The id of whoever makes the change comes from X-User-Id, so they cannot deactivate themselves
		verify(superadminService).setActive(3L, false, SUPERADMIN);
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
