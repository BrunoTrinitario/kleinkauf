package com.kleinkauf.sales;

import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.stock.ReservationDto;
import com.kleinkauf.common.dto.stock.ReservationItemType;
import com.kleinkauf.common.dto.stock.ReservationRequestDto;
import com.kleinkauf.common.dto.stock.ReservedItemDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.security.CustomUserDetails;
import com.kleinkauf.sales.dto.AdminOrderDto;
import com.kleinkauf.sales.dto.BuyerDto;
import com.kleinkauf.sales.dto.LocalOrderDto;
import com.kleinkauf.sales.dto.LocalOrderItemDto;
import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.SalesChannel;
import com.kleinkauf.sales.repositories.OrderRepository;
import com.kleinkauf.sales.services.OrderEvents;
import com.kleinkauf.sales.services.AdminOrderService;
import com.kleinkauf.sales.services.OrderService;
import com.kleinkauf.sales.services.OrderStock;
import com.kleinkauf.sales.services.ProductsClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// In-store sale (AdminOrderService.createLocal) with the real OrderStock over the mocked products client
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalSaleTest {

	private static final String NUMBER = "LOCA-L001";
	private static final long ADMIN = 1L;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderService orderService;

	@Mock
	private ProductsClient productsClient;
	@Mock
	private AuditLog auditLog;


	@Mock
	private OrderEvents orderEvents;

	@InjectMocks
	private AdminOrderService service;

	@BeforeEach
	void setUp() throws ApiException {
		OrderStock orderStock = new OrderStock();
		ReflectionTestUtils.setField(orderStock, "productsClient", productsClient);
		ReflectionTestUtils.setField(service, "orderStock", orderStock);
		ReflectionTestUtils.setField(service, "maxLines", 100);
		ReflectionTestUtils.setField(service, "maxQuantity", 999);
		when(orderService.newNumber()).thenReturn(NUMBER);
		when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
		CustomUserDetails admin = new CustomUserDetails(ADMIN, List.of(new RoleDto("ADMINISTRADOR", 31)));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void sale_withChangedPriceDiscountAndWithoutStock_delivered() throws ApiException {
		reserves(List.of("Silla"),
				reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", "60.00"),
				reserved(ReservationItemType.PRODUCT, 6L, "Silla", null, null));

		AdminOrderDto sale = service.createLocal(new LocalOrderDto(List.of(
				new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 2, new BigDecimal("90.00")),
				new LocalOrderItemDto(ReservationItemType.PRODUCT, 6L, 1, new BigDecimal("30.00"))),
				new BigDecimal("10.00"), PaymentMethod.EFECTIVO, OrderStatus.ENTREGADO, null, null, null, null));

		assertEquals(SalesChannel.LOCAL, sale.channel());
		assertEquals(OrderStatus.ENTREGADO, sale.status());
		assertEquals(PaymentMethod.EFECTIVO, sale.paymentMethod());
		// 2 * 90 + 30 - 10
		assertEquals(0, new BigDecimal("200.00").compareTo(sale.total()));
		assertEquals(new BigDecimal("100.00"), sale.items().get(0).listPrice());
		assertEquals(new BigDecimal("90.00"), sale.items().get(0).unitPrice());
		// 'A consultar': no list price
		assertNull(sale.items().get(1).listPrice());
		assertTrue(sale.stockReview());
		assertNull(sale.buyer().name());
		assertNull(sale.expiresAt());
		// PAGADO and ENTREGADO, both by the administrator
		assertEquals(2, sale.history().size());
		assertEquals(ADMIN, sale.history().get(0).userId());
		assertTrue(sale.history().get(0).reason().contains("Silla"));

		ArgumentCaptor<ReservationRequestDto> request = ArgumentCaptor.forClass(ReservationRequestDto.class);
		verify(productsClient).reserve(request.capture());
		assertTrue(request.getValue().local());
		verify(productsClient).markSold(eq(NUMBER), any());
		verify(productsClient, never()).release(anyString());
		verify(orderEvents).created(any());
	}

	@Test
	void sale_priceOnRequestWithoutPrice_400AndReturnsTheStock() {
		reserves(List.of(), reserved(ReservationItemType.PRODUCT, 6L, "Silla", null, null));

		ApiException e = assertThrows(ApiException.class, () -> service.createLocal(sale(
				new LocalOrderItemDto(ReservationItemType.PRODUCT, 6L, 1, null), null)));

		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
		assertTrue(e.getMessage().contains("Silla"));
		verify(productsClient).release(NUMBER);
		verify(productsClient, never()).markSold(anyString(), any());
		verify(orderRepository, never()).save(any());
	}

	@Test
	void sale_discountGreaterThanSubtotal_400AndReturnsTheStock() {
		reserves(List.of(), reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", null));

		ApiException e = assertThrows(ApiException.class, () -> service.createLocal(sale(
				new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 1, null), new BigDecimal("100.01"))));

		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
		verify(productsClient).release(NUMBER);
	}

	@Test
	void sale_ifItCannotAddToSold_returnsTheStockAnd503() {
		reserves(List.of(), reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", null));
		doThrow(new ResourceAccessException("timeout")).when(productsClient).markSold(eq(NUMBER), any());

		ApiException e = assertThrows(ApiException.class, () -> service.createLocal(sale(
				new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 1, null), null)));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
		verify(productsClient).release(NUMBER);
		verify(orderRepository, never()).save(any());
	}

	@Test
	void sale_ifItCannotBeSaved_rollsBackInProducts() {
		reserves(List.of(), reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", null));
		when(orderRepository.save(any(Order.class))).thenThrow(new IllegalStateException("db caida"));

		ApiException e = assertThrows(ApiException.class, () -> service.createLocal(sale(
				new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 1, null), null)));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
		verify(productsClient).unmarkSold(eq(NUMBER), any());
		verify(productsClient).release(NUMBER);
	}

	@Test
	void sale_validations() {
		LocalOrderItemDto ok = new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 1, null);
		// Initial status, payment method, negative discount, invalid buyer, quantity
		assertBadRequest(new LocalOrderDto(List.of(ok), null, PaymentMethod.EFECTIVO, OrderStatus.PENDIENTE_PAGO, null, null, null, null));
		assertBadRequest(new LocalOrderDto(List.of(ok), null, null, OrderStatus.PAGADO, null, null, null, null));
		assertBadRequest(new LocalOrderDto(List.of(ok), new BigDecimal("-1"), PaymentMethod.EFECTIVO, OrderStatus.PAGADO, null, null, null, null));
		assertBadRequest(new LocalOrderDto(List.of(ok), null, PaymentMethod.EFECTIVO, OrderStatus.PAGADO,
				new BuyerDto(null, "no-es-email", null), null, null, null));
		assertBadRequest(new LocalOrderDto(List.of(new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 1000, null)),
				null, PaymentMethod.EFECTIVO, OrderStatus.PAGADO, null, null, null, null));
		assertBadRequest(new LocalOrderDto(List.of(new LocalOrderItemDto(ReservationItemType.PRODUCT, 5L, 1, new BigDecimal("-5"))),
				null, PaymentMethod.EFECTIVO, OrderStatus.PAGADO, null, null, null, null));
		verify(productsClient, never()).reserve(any());
	}

	private void assertBadRequest(LocalOrderDto dto) {
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.createLocal(dto)).getStatus());
	}

	private static LocalOrderDto sale(LocalOrderItemDto item, BigDecimal discount) {
		return new LocalOrderDto(List.of(item), discount, PaymentMethod.EFECTIVO, OrderStatus.PAGADO, null, null, null, null);
	}

	private void reserves(List<String> shortages, ReservedItemDto... items) {
		when(productsClient.reserve(any())).thenReturn(new ReservationDto(NUMBER, List.of(items), shortages));
	}

	private static ReservedItemDto reserved(ReservationItemType type, Long id, String name, String price, String cost) {
		return new ReservedItemDto(type, id, name, price == null ? null : new BigDecimal(price), cost == null ? null : new BigDecimal(cost));
	}
}
