package com.decor.sales;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.dto.SalesSettingsDto;
import com.decor.sales.entities.SalesSettings;
import com.decor.sales.repositories.SalesSettingsRepository;
import com.decor.sales.services.SalesSettingsService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesSettingsServiceTest {

	@Mock
	private SalesSettingsRepository repository;

	@InjectMocks
	private SalesSettingsService service;

	@Test
	void sinConfigurar_valoresPorDefectoYSinDatosDePago() {
		when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

		SalesSettingsDto settings = service.get();

		assertEquals(48, settings.expirationHours());
		assertNull(settings.alias());
		assertFalse(SalesSettingsService.isPaymentConfigured(new SalesSettings()));
	}

	@Test
	void guardar_limpiaBlancosYUsaElPlazoPorDefecto() throws ApiException {
		when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
		when(repository.save(any(SalesSettings.class))).thenAnswer(i -> i.getArgument(0));

		SalesSettingsDto saved = service.update(new SalesSettingsDto(" tienda.decor ", "", "Decor SRL", null, null));

		assertEquals("tienda.decor", saved.alias());
		assertNull(saved.cbu());
		assertEquals(48, saved.expirationHours());
	}

	@Test
	void datosDePago_titularYAliasOCbu() {
		SalesSettings settings = new SalesSettings();
		settings.setHolder("Decor SRL");
		assertFalse(SalesSettingsService.isPaymentConfigured(settings));
		settings.setCbu("0123456789012345678901");
		assertTrue(SalesSettingsService.isPaymentConfigured(settings));
	}

	@Test
	void guardar_validaAliasCbuYPlazo() {
		assertBadRequest(new SalesSettingsDto("corto", null, "Decor", null, 48));
		assertBadRequest(new SalesSettingsDto("con espacios", null, "Decor", null, 48));
		assertBadRequest(new SalesSettingsDto(null, "123", "Decor", null, 48));
		assertBadRequest(new SalesSettingsDto("tienda.decor", null, "Decor", null, 0));
		assertBadRequest(new SalesSettingsDto("tienda.decor", null, "Decor", null, 721));
	}

	private void assertBadRequest(SalesSettingsDto dto) {
		ApiException e = assertThrows(ApiException.class, () -> service.update(dto));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
	}
}
