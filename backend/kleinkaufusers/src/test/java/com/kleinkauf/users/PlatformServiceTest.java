package com.kleinkauf.users;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.dtos.LoggedUser;
import com.kleinkauf.users.services.PlatformService;
import com.kleinkauf.users.services.UserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

	@Mock
	private UserService userService;

	@InjectMocks
	private PlatformService platformService;

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void loginDeSuperadmin() throws ApiException {
		LoggedUser user = user("SUPERADMIN");
		when(userService.login("root@kleinkauf.com", "clave")).thenReturn(user);

		assertSame(user, platformService.login("root@kleinkauf.com", "clave"));
	}

	@Test
	void loginSinRolSuperadmin_respondeUnauthorized() throws ApiException {
		when(userService.login("ana@mail.com", "clave")).thenReturn(user("ADMINISTRADOR"));

		ApiException ex = assertThrows(ApiException.class, () -> platformService.login("ana@mail.com", "clave"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
	}

	@Test
	void desdeUnTenant_noExisteLaPlataforma() {
		TenantContext.setCurrentTenant("tienda_a");

		ApiException ex = assertThrows(ApiException.class, () -> platformService.login("root@kleinkauf.com", "clave"));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		verifyNoInteractions(userService);
	}

	private static LoggedUser user(String role) {
		return new LoggedUser("Nombre", 1L, "mail", "token", List.of(new RoleDto(role, 0)));
	}
}
