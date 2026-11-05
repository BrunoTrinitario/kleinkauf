package com.kleinkauf.sales;

import com.kleinkauf.common.dto.mail.OrderConfirmationMailDto;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.sales.dto.PaymentDetailsDto;
import com.kleinkauf.sales.entities.Address;
import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderItem;
import com.kleinkauf.sales.services.OrderMail;
import com.kleinkauf.sales.services.UsersClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// The online order is summarized and users is asked to email it; if users fails, the order is unaffected
@ExtendWith(MockitoExtension.class)
class OrderMailTest {

	@Mock
	private UsersClient usersClient;

	@InjectMocks
	private OrderMail orderMail;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(orderMail, "executor", new SyncTaskExecutor());
		TenantContext.setCurrentTenant("tienda1");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void sendsTheSummaryToUsers() {
		orderMail.confirmation(order(), new PaymentDetailsDto("tienda.alias", null, "Ana SRL", null));

		ArgumentCaptor<OrderConfirmationMailDto> mail = ArgumentCaptor.forClass(OrderConfirmationMailDto.class);
		verify(usersClient).sendOrderConfirmation(mail.capture(), eq("tienda1"));
		OrderConfirmationMailDto sent = mail.getValue();
		assertEquals("K7QM-4X2P", sent.number());
		assertEquals("ana@mail.com", sent.buyerEmail());
		assertEquals(1, sent.items().size());
		assertEquals("Mesa", sent.items().get(0).name());
		assertEquals(2, sent.items().get(0).quantity());
		assertEquals("ENVIO", sent.deliveryType());
		assertEquals("Av. Siempreviva 742, 3° B, Springfield, Buenos Aires (1234)", sent.deliveryAddress());
		assertEquals("tienda.alias", sent.payment().alias());
	}

	@Test
	void withoutEmail_sendsNothing() {
		Order order = order();
		order.setBuyerEmail(null);

		orderMail.confirmation(order, null);

		verify(usersClient, never()).sendOrderConfirmation(any(), anyString());
	}

	@Test
	void usersDown_doesNotBreakTheOrder() {
		doThrow(new IllegalStateException("users caido")).when(usersClient).sendOrderConfirmation(any(), anyString());

		assertDoesNotThrow(() -> orderMail.confirmation(order(), null));
	}

	@Test
	void pickup_hasNoAddress() {
		Order order = order();
		order.setDeliveryType(DeliveryType.RETIRO);
		order.setAddress(null);

		orderMail.confirmation(order, null);

		ArgumentCaptor<OrderConfirmationMailDto> mail = ArgumentCaptor.forClass(OrderConfirmationMailDto.class);
		verify(usersClient).sendOrderConfirmation(mail.capture(), eq("tienda1"));
		assertEquals("RETIRO", mail.getValue().deliveryType());
		assertNull(mail.getValue().deliveryAddress());
		assertNull(mail.getValue().payment());
	}

	private static Order order() {
		Order order = new Order();
		order.setNumber("K7QM-4X2P");
		order.setBuyerName("Ana");
		order.setBuyerEmail("ana@mail.com");
		order.setDeliveryType(DeliveryType.ENVIO);
		order.setTotal(new BigDecimal("200.00"));
		Address address = new Address();
		address.setStreet("Av. Siempreviva");
		address.setNumber("742");
		address.setApartment("3° B");
		address.setCity("Springfield");
		address.setProvince("Buenos Aires");
		address.setPostalCode("1234");
		order.setAddress(address);
		OrderItem item = new OrderItem();
		item.setName("Mesa");
		item.setQuantity(2);
		item.setSubtotal(new BigDecimal("200.00"));
		order.getItems().add(item);
		return order;
	}
}
