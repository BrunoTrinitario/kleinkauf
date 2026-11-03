package com.decor.sales;

import java.util.Map;
import com.decor.common.audit.AuditEvent;
import com.decor.common.audit.AuditLog;
import com.decor.common.dto.stock.ReservationItemType;
import com.decor.common.dto.stock.SoldItemsDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.SalesSettings;
import com.decor.sales.repositories.OrderRepository;
import com.decor.sales.services.OrderStatusService;
import com.decor.sales.services.OrderStock;
import com.decor.sales.services.ProductsClient;
import com.decor.sales.services.SalesSettingsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderStatusServiceTest {

	private static final String NUMBER = "K7QM-4X2P";
	private static final Long ADMIN = 1L;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private SalesSettingsService settingsService;

	@Mock
	private ProductsClient productsClient;
	@Mock
	private AuditLog auditLog;


	@InjectMocks
	private OrderStatusService service;

	@BeforeEach
	void setUp() {
		OrderStock orderStock = new OrderStock();
		ReflectionTestUtils.setField(orderStock, "productsClient", productsClient);
		ReflectionTestUtils.setField(service, "orderStock", orderStock);
		when(settingsService.current()).thenReturn(new SalesSettings());
		when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void transicionesPermitidas() {
		assertTrue(OrderStatus.PAGO_EN_REVISION.canChangeTo(OrderStatus.PAGADO));
		assertTrue(OrderStatus.PENDIENTE_PAGO.canChangeTo(OrderStatus.PAGADO));
		assertTrue(OrderStatus.PAGO_EN_REVISION.canChangeTo(OrderStatus.RECHAZADO));
		assertTrue(OrderStatus.PAGADO.canChangeTo(OrderStatus.ENTREGADO));
		assertTrue(OrderStatus.EN_PREPARACION.canChangeTo(OrderStatus.LISTO));
		assertTrue(OrderStatus.LISTO.canChangeTo(OrderStatus.CANCELADO));

		assertFalse(OrderStatus.PENDIENTE_PAGO.canChangeTo(OrderStatus.RECHAZADO));
		assertFalse(OrderStatus.PENDIENTE_PAGO.canChangeTo(OrderStatus.EN_PREPARACION));
		assertFalse(OrderStatus.LISTO.canChangeTo(OrderStatus.EN_PREPARACION));
		assertFalse(OrderStatus.PAGADO.canChangeTo(OrderStatus.PAGADO));
		assertFalse(OrderStatus.PAGADO.canChangeTo(OrderStatus.VENCIDO));
		assertFalse(OrderStatus.ENTREGADO.canChangeTo(OrderStatus.CANCELADO));
		assertFalse(OrderStatus.VENCIDO.canChangeTo(OrderStatus.PAGADO));
	}

	@Test
	void validar_sumaAVendidosYGuardaElHistorial() throws ApiException {
		Order order = order(OrderStatus.PAGO_EN_REVISION);

		service.change(order, OrderStatus.PAGADO, ADMIN, null);

		assertEquals(OrderStatus.PAGADO, order.getStatus());
		assertEquals(ADMIN, order.getHistory().get(order.getHistory().size() - 1).getUserId());
		ArgumentCaptor<SoldItemsDto> sold = ArgumentCaptor.forClass(SoldItemsDto.class);
		verify(productsClient).markSold(eq(NUMBER), sold.capture());
		assertEquals(2, sold.getValue().items().get(0).quantity());
	}

	@Test
	void validar_siOtroCambioElPedido_restaDeVendidosY409() {
		Order order = order(OrderStatus.PAGO_EN_REVISION);
		when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L));

		ApiException e = assertThrows(ApiException.class, () -> service.change(order, OrderStatus.PAGADO, ADMIN, null));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		verify(productsClient).unmarkSold(eq(NUMBER), any());
	}

	@Test
	void rechazar_pideMotivoYRenuevaElPlazo() throws ApiException {
		Order order = order(OrderStatus.PAGO_EN_REVISION);
		order.setExpiresAt(LocalDateTime.now().minusHours(5));

		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
				() -> service.change(order, OrderStatus.RECHAZADO, ADMIN, " ")).getStatus());

		service.change(order, OrderStatus.RECHAZADO, ADMIN, "El monto no coincide");

		assertEquals(OrderStatus.RECHAZADO, order.getStatus());
		assertTrue(order.getExpiresAt().isAfter(LocalDateTime.now().plusHours(47)));
		assertEquals("El monto no coincide", order.getHistory().get(order.getHistory().size() - 1).getReason());
		// Queda en la auditoria con el estado anterior, el nuevo y el motivo
		verify(auditLog).record(AuditEvent.STATUS_CHANGE, "Order", order.getNumber(), Map.of(
				"status", new AuditEvent.Change("PAGO_EN_REVISION", "RECHAZADO"),
				"reason", new AuditEvent.Change(null, "El monto no coincide")));
	}

	@Test
	void cancelarPagado_devuelveStockYRestaDeVendidos() throws ApiException {
		Order order = order(OrderStatus.LISTO);

		service.change(order, OrderStatus.CANCELADO, ADMIN, "Se arrepintio");

		verify(productsClient).release(NUMBER);
		verify(productsClient).unmarkSold(eq(NUMBER), any());
		assertEquals(OrderStatus.CANCELADO, order.getStatus());
	}

	@Test
	void cancelarSinPagar_soloDevuelveStock() throws ApiException {
		Order order = order(OrderStatus.PENDIENTE_PAGO);

		service.change(order, OrderStatus.CANCELADO, ADMIN, "Sin stock real");

		verify(productsClient).release(NUMBER);
		verify(productsClient, never()).unmarkSold(anyString(), any());
	}

	@Test
	void cancelar_siProductsNoResponde_noCambiaElPedido() {
		Order order = order(OrderStatus.PAGADO);
		doThrow(new ResourceAccessException("timeout")).when(productsClient).release(NUMBER);

		ApiException e = assertThrows(ApiException.class, () -> service.change(order, OrderStatus.CANCELADO, ADMIN, "x"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
		assertEquals(OrderStatus.PAGADO, order.getStatus());
		verify(orderRepository, never()).saveAndFlush(any());
	}

	@Test
	void transicionInvalida_409SinTocarProducts() {
		Order order = order(OrderStatus.ENTREGADO);

		ApiException e = assertThrows(ApiException.class, () -> service.change(order, OrderStatus.CANCELADO, ADMIN, "x"));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		verify(productsClient, never()).release(anyString());
	}

	@Test
	void comprador_cancelaHastaQueSeValidaElPago() throws ApiException {
		Order pendiente = order(OrderStatus.PAGO_EN_REVISION);
		service.cancelByBuyer(pendiente, null);
		assertEquals(OrderStatus.CANCELADO, pendiente.getStatus());
		assertEquals(OrderStatusService.BUYER_CANCEL_REASON, pendiente.getHistory().get(pendiente.getHistory().size() - 1).getReason());

		Order pagado = order(OrderStatus.PAGADO);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.cancelByBuyer(pagado, null)).getStatus());
	}

	@Test
	void vencer_soloSiPasoElPlazoYEsperaElComprobante() throws ApiException {
		LocalDateTime now = LocalDateTime.now();
		Order vencido = order(OrderStatus.RECHAZADO);
		vencido.setExpiresAt(now.minusMinutes(1));
		Order enPlazo = order(OrderStatus.PENDIENTE_PAGO);
		enPlazo.setExpiresAt(now.plusMinutes(1));
		Order enRevision = order(OrderStatus.PAGO_EN_REVISION);
		enRevision.setExpiresAt(now.minusMinutes(1));

		assertTrue(service.expire(vencido, now));
		assertFalse(service.expire(enPlazo, now));
		assertFalse(service.expire(enRevision, now));

		assertEquals(OrderStatus.VENCIDO, vencido.getStatus());
		verify(productsClient).release(NUMBER);
	}

	private static Order order(OrderStatus status) {
		Order order = new Order();
		order.setNumber(NUMBER);
		OrderItem item = new OrderItem();
		item.setType(ReservationItemType.PRODUCT);
		item.setItemId(5L);
		item.setName("Mesa");
		item.setQuantity(2);
		item.setUnitPrice(new BigDecimal("100.00"));
		item.setSubtotal(new BigDecimal("200.00"));
		order.addItem(item);
		order.changeStatus(status, null, null);
		return order;
	}
}
