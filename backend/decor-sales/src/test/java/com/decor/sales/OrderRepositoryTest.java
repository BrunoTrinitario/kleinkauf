package com.decor.sales;

import com.decor.common.dto.stock.ReservationItemType;
import com.decor.sales.dto.AdminOrderDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PageDto;
import com.decor.sales.entities.Address;
import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.PaymentReceipt;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.repositories.OrderRepository;
import com.decor.sales.services.AdminOrderService;
import com.decor.sales.services.OrderService;
import com.decor.sales.services.OrderStatusService;
import com.decor.sales.services.OrderStock;
import com.decor.sales.util.ReceiptStorage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
Valida contra H2 (modo PostgreSQL) el mapeo de los pedidos y las consultas del panel,
que si estan mal solo fallan al arrancar el servicio o al usarlas.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:sales;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdminOrderService.class)
class OrderRepositoryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private AdminOrderService adminOrderService;

	@MockitoBean
	private OrderService orderService;

	@MockitoBean
	private OrderStatusService orderStatusService;

	@MockitoBean
	private OrderStock orderStock;

	@MockitoBean
	private ReceiptStorage receiptStorage;

	@Test
	void vencidos_soloSinComprobanteYConElPlazoCumplido() {
		LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
		Order vencido = order("AAAA-AAAA", "a@mail.com", null, OrderStatus.PENDIENTE_PAGO, SalesChannel.ONLINE, now.minusDays(3));
		vencido.setExpiresAt(now.minusMinutes(1));
		Order rechazado = order("BBBB-BBBB", "b@mail.com", null, OrderStatus.RECHAZADO, SalesChannel.ONLINE, now.minusDays(3));
		rechazado.setExpiresAt(now.minusHours(1));
		Order enPlazo = order("CCCC-CCCC", "c@mail.com", null, OrderStatus.PENDIENTE_PAGO, SalesChannel.ONLINE, now);
		enPlazo.setExpiresAt(now.plusHours(1));
		Order enRevision = order("DDDD-DDDD", "d@mail.com", null, OrderStatus.PAGO_EN_REVISION, SalesChannel.ONLINE, now.minusDays(3));
		enRevision.setExpiresAt(now.minusHours(1));
		List.of(vencido, rechazado, enPlazo, enRevision).forEach(em::persist);
		em.flush();

		List<String> numbers = orderRepository.findExpiredNumbers(
				EnumSet.of(OrderStatus.PENDIENTE_PAGO, OrderStatus.RECHAZADO), now);

		assertEquals(List.of("AAAA-AAAA", "BBBB-BBBB"), numbers.stream().sorted().toList());
	}

	@Test
	void comprobantes_seGuardanConElPedido_yLaVersionSube() throws Exception {
		Order order = order("AAAA-AAAA", "a@mail.com", null, OrderStatus.PENDIENTE_PAGO, SalesChannel.ONLINE, LocalDateTime.now());
		em.persistAndFlush(order);
		long version = order.getVersion();
		PaymentReceipt receipt = new PaymentReceipt();
		receipt.setPath("tienda1/AAAA-AAAA/a.png");
		receipt.setContentType("image/png");
		receipt.setSize(1234);
		order.addReceipt(receipt);
		order.changeStatus(OrderStatus.PAGO_EN_REVISION, null, null);
		em.flush();
		em.clear();

		AdminOrderDto loaded = adminOrderService.get("AAAA-AAAA");

		assertEquals(1, loaded.receipts().size());
		assertEquals("image/png", loaded.receipts().get(0).contentType());
		assertTrue(orderRepository.findByNumber("AAAA-AAAA").orElseThrow().getVersion() > version);
	}

	@Test
	void guardaElPedidoConItemsHistorialYDireccion() throws Exception {
		Order order = order("K7QM-4X2P", "ana@mail.com", 7L, OrderStatus.PENDIENTE_PAGO, SalesChannel.ONLINE, LocalDateTime.now());
		order.setDeliveryType(DeliveryType.ENVIO);
		order.setAddress(new Address("Calle", "123", "2B", "CABA", "Buenos Aires", "1405"));
		em.persistAndFlush(order);
		em.clear();

		AdminOrderDto loaded = adminOrderService.get("k7qm-4x2p");

		assertEquals(2, loaded.items().size());
		assertEquals(new BigDecimal("60.00"), loaded.items().get(0).unitCost());
		assertEquals(1, loaded.history().size());
		assertEquals(7L, loaded.history().get(0).userId());
		assertEquals("2B", loaded.address().apartment());
	}

	@Test
	void misPedidos_soloLosDelUsuario_masNuevosPrimero() {
		em.persist(order("AAAA-AAAA", "ana@mail.com", 7L, OrderStatus.PENDIENTE_PAGO, SalesChannel.ONLINE, LocalDateTime.now().minusDays(1)));
		em.persist(order("BBBB-BBBB", "ana@mail.com", 7L, OrderStatus.PAGADO, SalesChannel.ONLINE, LocalDateTime.now()));
		em.persist(order("CCCC-CCCC", "otro@mail.com", 8L, OrderStatus.PAGADO, SalesChannel.ONLINE, LocalDateTime.now()));
		em.flush();

		List<String> numbers = orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(7L, PageRequest.of(0, 10))
				.map(Order::getNumber).getContent();

		assertEquals(List.of("BBBB-BBBB", "AAAA-AAAA"), numbers);
	}

	@Test
	void panel_filtraPorEstadoCanalFechasYBusqueda() throws Exception {
		LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
		em.persist(order("AAAA-AAAA", "ana@mail.com", null, OrderStatus.PENDIENTE_PAGO, SalesChannel.ONLINE, now.minusDays(3)));
		em.persist(order("BBBB-BBBB", "juan_perez@mail.com", null, OrderStatus.PAGADO, SalesChannel.ONLINE, now));
		Order local = order("CCCC-CCCC", null, null, OrderStatus.ENTREGADO, SalesChannel.LOCAL, now);
		local.setPaymentMethod(PaymentMethod.EFECTIVO);
		local.setStockReview(true);
		em.persist(local);
		em.flush();
		em.clear();

		assertEquals(List.of("BBBB-BBBB"), numbers(filter("pagado", null, null, null, null, null, null)));
		assertEquals(List.of("CCCC-CCCC"), numbers(filter(null, "LOCAL", "efectivo", true, null, null, null)));
		// Rango inclusive por dia
		assertEquals(List.of("AAAA-AAAA"), numbers(filter(null, null, null, null, "2026-10-30", "2026-10-30", null)));
		assertEquals(2, numbers(filter(null, null, null, null, "2026-11-02", "2026-11-02", null)).size());
		// Por numero exacto o parte del email; el '_' se busca tal cual
		assertEquals(List.of("AAAA-AAAA"), numbers(filter(null, null, null, null, null, null, "aaaa-aaaa")));
		assertEquals(List.of("BBBB-BBBB"), numbers(filter(null, null, null, null, null, null, "juan_")));
		// Sin escapar, 'na_' coincidiria con 'ana@'
		assertTrue(numbers(filter(null, null, null, null, null, null, "na_")).isEmpty());
	}

	@Test
	void panel_resumenSumaLasUnidades() throws Exception {
		em.persistAndFlush(order("AAAA-AAAA", "ana@mail.com", null, OrderStatus.PAGADO, SalesChannel.ONLINE, LocalDateTime.now()));
		em.clear();

		PageDto<OrderSummaryDto> page = adminOrderService.list(filter(null, null, null, null, null, null, null), 0, 10);

		assertEquals(1, page.totalItems());
		assertEquals(3, page.items().get(0).units());
	}

	private List<String> numbers(AdminOrderService.Filter filter) throws Exception {
		return adminOrderService.list(filter, 0, 50).items().stream().map(OrderSummaryDto::number).toList();
	}

	private static AdminOrderService.Filter filter(String status, String channel, String paymentMethod, Boolean stockReview,
												   String from, String to, String q) {
		return new AdminOrderService.Filter(status, channel, paymentMethod, stockReview, from, to, q);
	}

	private static Order order(String number, String email, Long userId, OrderStatus status, SalesChannel channel, LocalDateTime createdAt) {
		Order order = new Order();
		order.setNumber(number);
		order.setChannel(channel);
		order.setPaymentMethod(PaymentMethod.TRANSFERENCIA);
		order.setBuyerName("Ana");
		order.setBuyerEmail(email);
		order.setUserId(userId);
		order.setDeliveryType(DeliveryType.RETIRO);
		order.addItem(item(ReservationItemType.PRODUCT, 5L, "Mesa", 1, "100.00", "60.00"));
		order.addItem(item(ReservationItemType.BUNDLE, 9L, "Comedor", 2, "150.00", null));
		order.setTotal(new BigDecimal("400.00"));
		order.changeStatus(status, userId, null);
		order.setCreatedAt(createdAt);
		return order;
	}

	private static OrderItem item(ReservationItemType type, Long id, String name, int quantity, String price, String cost) {
		OrderItem item = new OrderItem();
		item.setType(type);
		item.setItemId(id);
		item.setName(name);
		item.setQuantity(quantity);
		item.setListPrice(new BigDecimal(price));
		item.setUnitPrice(new BigDecimal(price));
		item.setUnitCost(cost == null ? null : new BigDecimal(cost));
		item.setSubtotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
		return item;
	}
}
