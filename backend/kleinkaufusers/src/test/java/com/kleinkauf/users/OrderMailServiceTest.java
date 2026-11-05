package com.kleinkauf.users;

import com.kleinkauf.common.dto.mail.OrderConfirmationMailDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.services.MailService;
import com.kleinkauf.users.services.OrderMailService;
import com.kleinkauf.users.services.StoreMailIdentity;
import com.kleinkauf.users.util.CacheUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Order confirmation: sent once per order, from the store, with the details to pay and follow the order
@ExtendWith(MockitoExtension.class)
class OrderMailServiceTest {

	private static final StoreMailIdentity.Sender STORE =
			new StoreMailIdentity.Sender("Lucía Deco", "duenia@mail.com", "https://lucia.kleinkauf.site");

	@Mock
	private MailService mailService;

	@Mock
	private StoreMailIdentity storeMailIdentity;

	@Mock
	private CacheUtil cacheUtil;

	@InjectMocks
	private OrderMailService orderMailService;

	private TimeZone previousZone;

	@BeforeEach
	void setUp() {
		previousZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")));
		ReflectionTestUtils.setField(orderMailService, "zone", "America/Argentina/Buenos_Aires");
		TenantContext.setCurrentTenant("lucia");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
		TimeZone.setDefault(previousZone);
	}

	@Test
	void sendsTheOrderFromTheStore() throws Exception {
		when(cacheUtil.claimOrderMail(eq("lucia"), eq("A1B2C3D4E"), any())).thenReturn(true);
		when(storeMailIdentity.current()).thenReturn(STORE);
		when(mailService.sendMail(eq(STORE), eq("ana@mail.com"), anyString(), anyString())).thenReturn(true);

		assertTrue(orderMailService.sendConfirmation(order("ENVIO", "Av. Siempreviva 742, Springfield")));

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mailService).sendMail(eq(STORE), eq("ana@mail.com"), subject.capture(), text.capture());
		assertEquals("Recibimos tu pedido A1B2C3D4E en Lucía Deco", subject.getValue());
		String body = text.getValue();
		assertTrue(body.startsWith("¡Hola Ana!"));
		assertTrue(body.contains("- 2 x Lámpara: $"));
		assertTrue(body.contains("Descuento: -$"));
		assertTrue(body.contains("Entrega: envío a Av. Siempreviva 742, Springfield"));
		assertTrue(body.contains("Alias: lucia.deco"));
		assertTrue(body.contains("antes del 07/11/2026 12:00 hs"), body);
		assertTrue(body.contains("https://lucia.kleinkauf.site/pedido/A1B2C3D4E"));
	}

	@Test
	void pickup_saysSo() throws Exception {
		when(cacheUtil.claimOrderMail(anyString(), anyString(), any())).thenReturn(true);
		when(storeMailIdentity.current()).thenReturn(STORE);
		when(mailService.sendMail(any(), anyString(), anyString(), anyString())).thenReturn(true);

		orderMailService.sendConfirmation(order("RETIRO", null));

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mailService).sendMail(any(), anyString(), anyString(), text.capture());
		assertTrue(text.getValue().contains("Entrega: retiro en la tienda"));
	}

	@Test
	void alreadySent_doesNotSendAgain() throws Exception {
		when(cacheUtil.claimOrderMail(anyString(), anyString(), any())).thenReturn(false);

		assertFalse(orderMailService.sendConfirmation(order("RETIRO", null)));
		verify(mailService, never()).sendMail(any(), anyString(), anyString(), anyString());
	}

	@Test
	void mailFails_releasesTheMarkToTryAgain() throws Exception {
		when(cacheUtil.claimOrderMail(anyString(), anyString(), any())).thenReturn(true);
		when(storeMailIdentity.current()).thenReturn(STORE);
		when(mailService.sendMail(any(), anyString(), anyString(), anyString())).thenReturn(false);

		assertFalse(orderMailService.sendConfirmation(order("RETIRO", null)));
		verify(cacheUtil).releaseOrderMail("lucia", "A1B2C3D4E");
	}

	@Test
	void invalidEmail_respondsBadRequest() {
		OrderConfirmationMailDto order = new OrderConfirmationMailDto("A1B2C3D4E", "Ana", "no-es-un-mail",
				List.of(), BigDecimal.ZERO, BigDecimal.TEN, "RETIRO", null, null, null);

		assertThrows(ApiException.class, () -> orderMailService.sendConfirmation(order));
		verify(mailService, never()).sendMail(any(), anyString(), anyString(), anyString());
	}

	private static OrderConfirmationMailDto order(String delivery, String address) {
		return new OrderConfirmationMailDto(
				"A1B2C3D4E", "Ana", "ana@mail.com",
				List.of(new OrderConfirmationMailDto.Item("Lámpara", 2, new BigDecimal("30000.00"))),
				new BigDecimal("1500.00"), new BigDecimal("28500.00"),
				delivery, address,
				// 15:00 UTC = 12:00 in Argentina
				LocalDateTime.of(2026, 11, 7, 15, 0),
				new OrderConfirmationMailDto.Payment("lucia.deco", "0000003100000000000001", "Lucía Pérez", "Banco X"));
	}
}
