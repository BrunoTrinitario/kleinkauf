package com.kleinkauf.users;

import com.kleinkauf.common.security.PermissionArea;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.dtos.RolePermissionDto;
import com.kleinkauf.users.entities.RolePermission;
import com.kleinkauf.users.repositories.RolePermissionRepository;
import com.kleinkauf.users.services.RolePermissionService;
import com.kleinkauf.users.util.CacheUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

	@Mock
	private RolePermissionRepository repository;

	@Mock
	private CacheUtil cacheUtil;

	@InjectMocks
	private RolePermissionService service;

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void porDefecto_igualQueAntes() {
		when(repository.findByType(anyString())).thenReturn(Optional.empty());

		assertEquals(PermissionArea.STOCK.bit(), service.maskFor("EMPLEADO"));
		assertEquals(0, service.maskFor("CLIENTE"));
	}

	@Test
	void administradorSiempreTodo_superadminNada() {
		assertEquals(PermissionArea.ALL, service.maskFor("ADMINISTRADOR"));
		assertEquals(0, service.maskFor("SUPERADMIN"));
		assertEquals(0, service.maskFor("INVENTADO"));
		verifyNoInteractions(repository);
	}

	@Test
	void usaLaConfiguracionDeLaTienda() {
		when(repository.findByType("EMPLEADO")).thenReturn(Optional.of(config("EMPLEADO", 7)));

		assertEquals(7, service.maskFor("EMPLEADO"));
	}

	@Test
	void listaLosTresRoles() {
		when(repository.findByType(anyString())).thenReturn(Optional.empty());

		assertEquals(List.of(
				new RolePermissionDto("ADMINISTRADOR", 31),
				new RolePermissionDto("EMPLEADO", 4),
				new RolePermissionDto("CLIENTE", 0)
		), service.list());
	}

	@Test
	void configurarEmpleado_guardaYBorraLosRolesCacheadosDeLaTienda() throws ApiException {
		TenantContext.setCurrentTenant("tienda_a");
		when(repository.findByType("EMPLEADO")).thenReturn(Optional.empty());

		RolePermissionDto result = service.update("EMPLEADO", PermissionArea.PRODUCTOS.bit() | PermissionArea.PRECIOS.bit());

		ArgumentCaptor<RolePermission> saved = ArgumentCaptor.forClass(RolePermission.class);
		verify(repository).save(saved.capture());
		assertEquals("EMPLEADO", saved.getValue().getType());
		assertEquals(3, saved.getValue().getPermissions());
		assertEquals(new RolePermissionDto("EMPLEADO", 3), result);
		verify(cacheUtil).deleteTenantUserRoles("tienda_a");
	}

	@Test
	void usuariosNoSeDelega() {
		ApiException ex = assertThrows(ApiException.class, () -> service.update("EMPLEADO", PermissionArea.USUARIOS.bit()));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verify(repository, never()).save(any());
	}

	@Test
	void bitsDesconocidosONegativos_sonError() {
		for (int mask : List.of(32, 64, -1)) {
			ApiException ex = assertThrows(ApiException.class, () -> service.update("CLIENTE", mask), String.valueOf(mask));
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		}
	}

	@Test
	void administradorYSuperadminNoSeConfiguran() {
		for (String role : List.of("ADMINISTRADOR", "SUPERADMIN")) {
			ApiException ex = assertThrows(ApiException.class, () -> service.update(role, 0), role);
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		}
	}

	@Test
	void rolInexistente_respondeNotFound() {
		ApiException ex = assertThrows(ApiException.class, () -> service.update("VENDEDOR", 1));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	private static RolePermission config(String type, int permissions) {
		RolePermission rp = new RolePermission();
		rp.setType(type);
		rp.setPermissions(permissions);
		return rp;
	}
}
