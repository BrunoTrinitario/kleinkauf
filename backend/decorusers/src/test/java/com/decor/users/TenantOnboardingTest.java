package com.decor.users;

import com.decor.common.events.DecorEvent;
import com.decor.common.events.DecorEventPublisher;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.dtos.platform.NewTenantDto;
import com.decor.users.dtos.platform.PlatformTenantDto;
import com.decor.users.entities.Tenant;
import com.decor.users.repositories.TenantRepository;
import com.decor.users.services.TenantOwnerCreator;
import com.decor.users.services.TenantService;
import com.decor.users.util.CacheUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantOnboardingTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private CacheUtil cacheUtil;

	@Mock
	private TenantOwnerCreator tenantOwnerCreator;

	@Mock
	private DecorEventPublisher events;

	@InjectMocks
	private TenantService tenantService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(tenantService, "platformScheme", "http");
		ReflectionTestUtils.setField(tenantService, "platformDomain", "localhost:3000");
	}

	@Test
	void alta_creaTenantActivoConSuDuenio() throws ApiException {
		when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
			Tenant t = invocation.getArgument(0);
			t.setId(7L);
			return t;
		});

		PlatformTenantDto result = tenantService.createTenant(dto("mi-tienda"));

		ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
		verify(tenantRepository).save(saved.capture());
		Tenant tenant = saved.getValue();
		assertEquals("mi_tienda", tenant.getSchema());
		assertEquals("http://mi-tienda.localhost:3000", tenant.getUrl());
		assertTrue(tenant.getEnable());
		assertNotNull(UUID.fromString(tenant.getConfigId()));

		verify(tenantOwnerCreator).createOwner("mi_tienda", "Ana", "ana@mail.com", "secreta12");
		assertEquals("mi-tienda", result.identifier());
		assertEquals("http://mi-tienda.localhost:3000", result.url());
		assertTrue(result.enabled());
		verify(events).publish(DecorEvent.TENANT_CREATED, "public",
				Map.of("id", 7L, "identifier", "mi-tienda", "schema", "mi_tienda"));
	}

	@Test
	void identificadoresInvalidos_respondenBadRequest() {
		List<String> invalidos = List.of(
				"ab",                                   // muy corto
				"a".repeat(31),                         // muy largo
				"MiTienda",                             // mayusculas
				"1tienda",                              // empieza con numero
				"tienda-",                              // termina con guion
				"mi_tienda",                            // guion bajo
				"mi tienda",                            // espacio
				"tienda;drop schema public",            // inyeccion
				""
		);

		for (String identifier : invalidos) {
			ApiException ex = assertThrows(ApiException.class, () -> tenantService.createTenant(dto(identifier)), identifier);
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus(), identifier);
		}
		assertThrows(ApiException.class, () -> tenantService.createTenant(dto(null)));
		verifyNoInteractions(tenantRepository, tenantOwnerCreator);
	}

	@Test
	void identificadoresReservados_respondenBadRequest() {
		for (String identifier : List.of("admin", "www", "api", "public", "pg-catalog")) {
			ApiException ex = assertThrows(ApiException.class, () -> tenantService.createTenant(dto(identifier)), identifier);
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus(), identifier);
		}
		verifyNoInteractions(tenantRepository, tenantOwnerCreator);
	}

	@Test
	void identificadorEnUso_respondeConflicto() {
		when(tenantRepository.existsBySchema("mi_tienda")).thenReturn(true);

		ApiException ex = assertThrows(ApiException.class, () -> tenantService.createTenant(dto("mi-tienda")));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
		verify(tenantRepository, never()).save(any());
		verifyNoInteractions(tenantOwnerCreator);
	}

	@Test
	void altaSimultaneaConElMismoIdentificador_respondeConflicto() {
		when(tenantRepository.save(any(Tenant.class))).thenThrow(new DataIntegrityViolationException("unique schema"));

		ApiException ex = assertThrows(ApiException.class, () -> tenantService.createTenant(dto("mi-tienda")));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
		verifyNoInteractions(tenantOwnerCreator);
	}

	@Test
	void faltanDatosDelDuenio_respondeBadRequest() {
		ApiException ex = assertThrows(ApiException.class,
				() -> tenantService.createTenant(new NewTenantDto("mi-tienda", "Ana", " ", "secreta")));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verifyNoInteractions(tenantRepository, tenantOwnerCreator);
	}

	@Test
	void datosDelDuenioInvalidos_respondeBadRequest() {
		for (NewTenantDto dto : List.of(
				new NewTenantDto("mi-tienda", "Ana", "r", "secreta12"),
				new NewTenantDto("mi-tienda", "Ana", "ana@mail.com", "r"))) {
			ApiException ex = assertThrows(ApiException.class, () -> tenantService.createTenant(dto));
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		}
		verifyNoInteractions(tenantRepository, tenantOwnerCreator);
	}

	@Test
	void siFallaCrearAlDuenio_seDeshaceElAlta() {
		Tenant saved = new Tenant();
		when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
		RuntimeException error = new RuntimeException("db caida");
		doThrow(error).when(tenantOwnerCreator).createOwner(anyString(), anyString(), anyString(), anyString());

		RuntimeException thrown = assertThrows(RuntimeException.class, () -> tenantService.createTenant(dto("mi-tienda")));

		assertSame(error, thrown);
		verify(tenantRepository).delete(saved);
		verifyNoInteractions(events);
	}

	@Test
	void deshabilitar_guardaYLimpiaElCache() throws ApiException {
		Tenant tenant = existing();

		PlatformTenantDto result = tenantService.setEnabled(7L, false);

		assertFalse(tenant.getEnable());
		assertFalse(result.enabled());
		verify(tenantRepository).save(tenant);
		verify(cacheUtil).deleteTenant("mi_tienda", "http://mi-tienda.localhost:3000");
		verify(events).publish(eq(DecorEvent.TENANT_DISABLED), eq("public"), any());
	}

	@Test
	void borrar_esSoftDeleteYLimpiaElCache() throws ApiException {
		Tenant tenant = existing();

		tenantService.deleteTenant(7L);

		assertNotNull(tenant.getDeletedAt());
		verify(tenantRepository).save(tenant);
		verify(cacheUtil).deleteTenant("mi_tienda", "http://mi-tienda.localhost:3000");
		verify(events).publish(eq(DecorEvent.TENANT_DELETED), eq("public"), any());
	}

	@Test
	void tenantInexistenteOBorrado_respondeNotFound() {
		when(tenantRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> tenantService.setEnabled(9L, true));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	@Test
	void buscarPorUrl_cacheaPorUrlParaElGateway() {
		when(tenantRepository.findByUrlAndDeletedAtIsNull("http://mi-tienda.localhost:3000")).thenReturn(Optional.of(tenant()));

		tenantService.getTenantByUrl("http://mi-tienda.localhost:3000");

		verify(cacheUtil).saveTenantByUrl(eq("http://mi-tienda.localhost:3000"), any());
	}

	private Tenant existing() {
		Tenant tenant = tenant();
		when(tenantRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(tenant));
		return tenant;
	}

	private static Tenant tenant() {
		Tenant tenant = new Tenant();
		tenant.setId(7L);
		tenant.setSchema("mi_tienda");
		tenant.setUrl("http://mi-tienda.localhost:3000");
		tenant.setConfigId(UUID.randomUUID().toString());
		tenant.setEnable(true);
		return tenant;
	}

	private static NewTenantDto dto(String identifier) {
		return new NewTenantDto(identifier, "Ana", "ana@mail.com", "secreta12");
	}
}
