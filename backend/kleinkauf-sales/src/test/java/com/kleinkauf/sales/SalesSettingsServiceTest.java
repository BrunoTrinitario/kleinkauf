package com.kleinkauf.sales;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.dto.SalesSettingsDto;
import com.kleinkauf.sales.entities.SalesSettings;
import com.kleinkauf.sales.repositories.SalesSettingsRepository;
import com.kleinkauf.sales.services.SalesSettingsService;
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
	void notConfigured_defaultValuesAndNoPaymentDetails() {
		when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

		SalesSettingsDto settings = service.get();

		assertEquals(48, settings.expirationHours());
		assertNull(settings.alias());
		assertFalse(SalesSettingsService.isPaymentConfigured(new SalesSettings()));
	}

	@Test
	void save_trimsBlanksAndUsesTheDefaultDeadline() throws ApiException {
		when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
		when(repository.save(any(SalesSettings.class))).thenAnswer(i -> i.getArgument(0));

		SalesSettingsDto saved = service.update(new SalesSettingsDto(" tienda.kleinkauf ", "", "KleinKauf SRL", null, null));

		assertEquals("tienda.kleinkauf", saved.alias());
		assertNull(saved.cbu());
		assertEquals(48, saved.expirationHours());
	}

	@Test
	void paymentDetails_holderAndAliasOrCbu() {
		SalesSettings settings = new SalesSettings();
		settings.setHolder("KleinKauf SRL");
		assertFalse(SalesSettingsService.isPaymentConfigured(settings));
		settings.setCbu("0123456789012345678901");
		assertTrue(SalesSettingsService.isPaymentConfigured(settings));
	}

	@Test
	void save_validatesAliasCbuAndDeadline() {
		assertBadRequest(new SalesSettingsDto("corto", null, "KleinKauf", null, 48));
		assertBadRequest(new SalesSettingsDto("con espacios", null, "KleinKauf", null, 48));
		assertBadRequest(new SalesSettingsDto(null, "123", "KleinKauf", null, 48));
		assertBadRequest(new SalesSettingsDto("tienda.kleinkauf", null, "KleinKauf", null, 0));
		assertBadRequest(new SalesSettingsDto("tienda.kleinkauf", null, "KleinKauf", null, 721));
	}

	private void assertBadRequest(SalesSettingsDto dto) {
		ApiException e = assertThrows(ApiException.class, () -> service.update(dto));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
	}
}
