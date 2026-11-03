package com.decor.sales;

import com.decor.common.dto.RoleDto;
import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationItemType;
import com.decor.common.dto.stock.ReservationRequestDto;
import com.decor.common.dto.stock.ReservedItemDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.common.servlet.security.CustomUserDetails;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.sales.dto.AddressDto;
import com.decor.sales.dto.BuyerDto;
import com.decor.sales.dto.LookupDto;
import com.decor.sales.dto.NewOrderDto;
import com.decor.sales.dto.NewOrderItemDto;
import com.decor.sales.dto.OrderCreatedDto;
import com.decor.sales.dto.OrderDto;
import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.PaymentReceipt;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.entities.SalesSettings;
import com.decor.sales.repositories.OrderRepository;
import com.decor.sales.services.OrderEvents;
import com.decor.sales.services.OrderNumberGenerator;
import com.decor.sales.services.OrderService;
import com.decor.sales.services.OrderStatusService;
import com.decor.sales.services.OrderStock;
import com.decor.sales.services.PricesChangedException;
import com.decor.sales.services.ProductsClient;
import com.decor.sales.services.SalesSettingsService;
import com.decor.sales.util.ReceiptStorage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

	private static final String NUMBER = "K7QM-4X2P";

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private SalesSettingsService settingsService;

	@Mock
	private ProductsClient productsClient;

	@Mock
	private OrderNumberGenerator numberGenerator;

	@Mock
	private OrderStatusService statusService;

	@Mock
	private ReceiptStorage receiptStorage;

	@Mock
	private OrderEvents orderEvents;

	@InjectMocks
	private OrderService service;

	@BeforeEach
	void setUp() {
		// OrderStock real sobre el cliente mockeado: se prueba tambien el manejo de errores de products
		OrderStock orderStock = new OrderStock();
		ReflectionTestUtils.setField(orderStock, "productsClient", productsClient);
		ReflectionTestUtils.setField(service, "orderStock", orderStock);
		ReflectionTestUtils.setField(service, "maxLines", 30);
		ReflectionTestUtils.setField(service, "maxQuantity", 20);
		ReflectionTestUtils.setField(service, "maxReceipts", 5);
		ReflectionTestUtils.setField(service, "maxReceiptSize", 5L * 1024 * 1024);
		TenantContext.setCurrentTenant("tienda1");
		SalesSettings settings = new SalesSettings();
		settings.setAlias("tienda.decor");
		settings.setHolder("Decor SRL");
		when(settingsService.current()).thenReturn(settings);
		when(numberGenerator.next()).thenReturn(NUMBER);
		when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		TenantContext.clear();
	}

	private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

	@Test
	void comprobante_pasaARevisionYSeGuarda() throws Exception {
		Order order = pending();
		when(receiptStorage.save(eq("tienda1"), eq(NUMBER), any(), eq(ReceiptStorage.FileType.PNG))).thenReturn("tienda1/K7QM-4X2P/a.png");
		when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(i -> i.getArgument(0));

		OrderDto dto = service.uploadReceipt("k7qm-4x2p", "ANA@mail.com", file(PNG, "image/png"));

		assertEquals(OrderStatus.PAGO_EN_REVISION, dto.status());
		assertEquals(1, dto.receipts());
		// Ya no se muestran los datos para transferir
		assertNull(dto.payment());
		assertEquals("image/png", order.getReceipts().get(0).getContentType());
		// Panel en vivo de la tienda
		verify(orderEvents).receiptUploaded(order);
	}

	@Test
	void comprobante_tipoPorContenido_noPorElNombre() {
		pending();
		// Dice ser PNG pero no lo es
		ApiException e = assertThrows(ApiException.class,
				() -> service.uploadReceipt(NUMBER, "ana@mail.com", file("<html>".getBytes(StandardCharsets.UTF_8), "image/png")));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
		verify(receiptStorage, never()).save(any(), any(), any(), any());
	}

	@Test
	void comprobante_soloMientrasEsperaElPagoYAntesDeVencer() {
		Order vencido = pending();
		vencido.setExpiresAt(LocalDateTime.now().minusMinutes(1));
		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
				() -> service.uploadReceipt(NUMBER, "ana@mail.com", file(PNG, "image/png"))).getStatus());

		Order pagado = pending();
		pagado.setStatus(OrderStatus.PAGADO);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
				() -> service.uploadReceipt(NUMBER, "ana@mail.com", file(PNG, "image/png"))).getStatus());
	}

	@Test
	void comprobante_hasta5() {
		Order order = pending();
		for (int i = 0; i < 5; i++) order.addReceipt(new PaymentReceipt());

		ApiException e = assertThrows(ApiException.class,
				() -> service.uploadReceipt(NUMBER, "ana@mail.com", file(PNG, "image/png")));
		assertEquals(HttpStatus.CONFLICT, e.getStatus());
	}

	@Test
	void comprobante_otroEmail_404() {
		pending();
		ApiException e = assertThrows(ApiException.class,
				() -> service.uploadReceipt(NUMBER, "otro@mail.com", file(PNG, "image/png")));
		assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
	}

	@Test
	void comprobante_siNoSePuedeGuardarElPedido_borraElArchivo() {
		pending();
		when(receiptStorage.save(any(), any(), any(), any())).thenReturn("tienda1/K7QM-4X2P/a.png");
		when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L));

		ApiException e = assertThrows(ApiException.class,
				() -> service.uploadReceipt(NUMBER, "ana@mail.com", file(PNG, "image/png")));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		verify(receiptStorage).deleteQuietly("tienda1/K7QM-4X2P/a.png");
	}

	private Order pending() {
		Order order = new Order();
		order.setNumber(NUMBER);
		order.setBuyerEmail("ana@mail.com");
		order.setStatus(OrderStatus.PENDIENTE_PAGO);
		order.setExpiresAt(LocalDateTime.now().plusHours(1));
		when(orderRepository.findByNumber(NUMBER)).thenReturn(Optional.of(order));
		return order;
	}

	private static MockMultipartFile file(byte[] bytes, String contentType) {
		return new MockMultipartFile("file", "comprobante.png", contentType, bytes);
	}

	@Test
	void crear_reservaGuardaYDevuelveLosDatosParaTransferir() throws Exception {
		reserves(reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", "60.00"),
				reserved(ReservationItemType.BUNDLE, 9L, "Comedor", "150.00", null));

		OrderCreatedDto created = service.create(order(item(ReservationItemType.PRODUCT, 5L, 2, "100.00"),
				item(ReservationItemType.BUNDLE, 9L, 1, "150.00")));

		assertEquals(NUMBER, created.number());
		assertEquals(OrderStatus.PENDIENTE_PAGO, created.status());
		assertEquals(0, new BigDecimal("350.00").compareTo(created.total()));
		assertEquals("tienda.decor", created.payment().alias());

		ArgumentCaptor<ReservationRequestDto> request = ArgumentCaptor.forClass(ReservationRequestDto.class);
		verify(productsClient).reserve(request.capture());
		assertEquals(NUMBER, request.getValue().reference());
		assertEquals(2, request.getValue().items().size());

		Order order = savedOrder();
		assertEquals(SalesChannel.ONLINE, order.getChannel());
		assertEquals(PaymentMethod.TRANSFERENCIA, order.getPaymentMethod());
		assertEquals("ana@mail.com", order.getBuyerEmail());
		assertNull(order.getUserId());
		assertEquals(2, order.getItems().size());
		assertEquals(new BigDecimal("60.00"), order.getItems().get(0).getUnitCost());
		assertEquals(0, new BigDecimal("200.00").compareTo(order.getItems().get(0).getSubtotal()));
		assertEquals(1, order.getHistory().size());
		assertTrue(order.getExpiresAt().isAfter(order.getCreatedAt().plusHours(47)));
		verify(productsClient, never()).release(anyString());
		verify(orderEvents).created(order);
	}

	@Test
	void crear_logueado_quedaAsociadoAlUsuario() throws Exception {
		CustomUserDetails user = new CustomUserDetails(7L, List.of(new RoleDto("CLIENTE", 0)));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
		reserves(reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", null));

		service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00")));

		assertEquals(7L, savedOrder().getUserId());
	}

	@Test
	void crear_lineasRepetidas_seJuntan() throws Exception {
		reserves(reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", null));

		service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00"),
				item(ReservationItemType.PRODUCT, 5L, 2, "100.00")));

		ArgumentCaptor<ReservationRequestDto> request = ArgumentCaptor.forClass(ReservationRequestDto.class);
		verify(productsClient).reserve(request.capture());
		assertEquals(1, request.getValue().items().size());
		assertEquals(3, request.getValue().items().get(0).quantity());
	}

	@Test
	void crear_precioCambiado_devuelveElStockYNoGuarda() {
		reserves(reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "120.00", null));

		PricesChangedException e = assertThrows(PricesChangedException.class,
				() -> service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00"))));

		assertEquals(1, e.getChanges().size());
		assertEquals(new BigDecimal("120.00"), e.getChanges().get(0).currentPrice());
		verify(productsClient).release(NUMBER);
		verify(orderRepository, never()).save(any());
	}

	@Test
	void crear_sinStock_respondeElErrorDeProductsSinDevolver() {
		when(productsClient.reserve(any())).thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
				HttpHeaders.EMPTY, "Not enough stock: Mesa".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

		ApiException e = assertThrows(ApiException.class,
				() -> service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00"))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals("Not enough stock: Mesa", e.getMessage());
		// Todo o nada: products no desconto nada
		verify(productsClient, never()).release(anyString());
		verify(orderRepository, never()).save(any());
	}

	@Test
	void crear_productsNoResponde_devuelvePorLasDudasY503() {
		when(productsClient.reserve(any())).thenThrow(new ResourceAccessException("timeout"));

		ApiException e = assertThrows(ApiException.class,
				() -> service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00"))));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
		verify(productsClient).release(NUMBER);
	}

	@Test
	void crear_fallaAlGuardar_devuelveElStock() {
		reserves(reserved(ReservationItemType.PRODUCT, 5L, "Mesa", "100.00", null));
		when(orderRepository.save(any(Order.class))).thenThrow(new IllegalStateException("db caida"));

		ApiException e = assertThrows(ApiException.class,
				() -> service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00"))));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
		verify(productsClient).release(NUMBER);
	}

	@Test
	void crear_sinDatosDePago_esConflictoSinReservar() {
		when(settingsService.current()).thenReturn(new SalesSettings());

		ApiException e = assertThrows(ApiException.class,
				() -> service.create(order(item(ReservationItemType.PRODUCT, 5L, 1, "100.00"))));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		verify(productsClient, never()).reserve(any());
	}

	@Test
	void crear_validaTopesCompradorYDireccion() {
		NewOrderItemDto ok = item(ReservationItemType.PRODUCT, 5L, 1, "100.00");
		BuyerDto buyer = new BuyerDto("Ana", "ana@mail.com", "11 5555-5555");
		AddressDto address = new AddressDto("Calle", "123", null, "CABA", "Buenos Aires", "1405");

		// Mas de 20 unidades (tambien sumando lineas repetidas)
		assertBadRequest(new NewOrderDto(List.of(item(ReservationItemType.PRODUCT, 5L, 21, "1")), buyer, DeliveryType.RETIRO, null, null));
		assertBadRequest(new NewOrderDto(List.of(item(ReservationItemType.PRODUCT, 5L, 15, "1"), item(ReservationItemType.PRODUCT, 5L, 6, "1")),
				buyer, DeliveryType.RETIRO, null, null));
		// Mas de 30 lineas
		List<NewOrderItemDto> many = java.util.stream.LongStream.rangeClosed(1, 31)
				.mapToObj(id -> item(ReservationItemType.PRODUCT, id, 1, "1")).toList();
		assertBadRequest(new NewOrderDto(many, buyer, DeliveryType.RETIRO, null, null));
		// Sin precio del carrito, sin items
		assertBadRequest(new NewOrderDto(List.of(new NewOrderItemDto(ReservationItemType.PRODUCT, 5L, 1, null)), buyer, DeliveryType.RETIRO, null, null));
		assertBadRequest(new NewOrderDto(List.of(), buyer, DeliveryType.RETIRO, null, null));
		// Comprador
		assertBadRequest(new NewOrderDto(List.of(ok), new BuyerDto("Ana", "no-es-email", "11 5555-5555"), DeliveryType.RETIRO, null, null));
		assertBadRequest(new NewOrderDto(List.of(ok), new BuyerDto(" ", "ana@mail.com", "11 5555-5555"), DeliveryType.RETIRO, null, null));
		assertBadRequest(new NewOrderDto(List.of(ok), new BuyerDto("Ana", "ana@mail.com", null), DeliveryType.RETIRO, null, null));
		// Entrega
		assertBadRequest(new NewOrderDto(List.of(ok), buyer, null, null, null));
		assertBadRequest(new NewOrderDto(List.of(ok), buyer, DeliveryType.ENVIO, null, null));
		assertBadRequest(new NewOrderDto(List.of(ok), buyer, DeliveryType.ENVIO,
				new AddressDto("Calle", "123", null, "CABA", "Buenos Aires", " "), null));
		assertBadRequest(new NewOrderDto(List.of(ok), buyer, DeliveryType.ENVIO, address, "x".repeat(501)));

		verify(productsClient, never()).reserve(any());
	}

	@Test
	void consulta_conOtroEmail_noEncuentraElPedido() {
		Order order = new Order();
		order.setNumber(NUMBER);
		order.setBuyerEmail("ana@mail.com");
		when(orderRepository.findByNumber(NUMBER)).thenReturn(Optional.of(order));

		ApiException e = assertThrows(ApiException.class, () -> service.lookup(new LookupDto("otro@mail.com", NUMBER)));
		assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
	}

	@Test
	void consulta_sinImportarMayusculas() throws ApiException {
		Order order = new Order();
		order.setNumber(NUMBER);
		order.setStatus(OrderStatus.PENDIENTE_PAGO);
		order.setBuyerEmail("ana@mail.com");
		when(orderRepository.findByNumber(NUMBER)).thenReturn(Optional.of(order));

		assertEquals(NUMBER, service.lookup(new LookupDto(" ANA@mail.com ", "k7qm-4x2p")).number());
	}

	private void assertBadRequest(NewOrderDto dto) {
		ApiException e = assertThrows(ApiException.class, () -> service.create(dto));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
	}

	private void reserves(ReservedItemDto... items) {
		when(productsClient.reserve(any())).thenReturn(new ReservationDto(NUMBER, List.of(items), List.of()));
	}

	private Order savedOrder() {
		ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
		verify(orderRepository).save(captor.capture());
		return captor.getValue();
	}

	private static NewOrderDto order(NewOrderItemDto... items) {
		return new NewOrderDto(List.of(items), new BuyerDto("Ana", " Ana@Mail.com ", "11 5555-5555"),
				DeliveryType.RETIRO, null, null);
	}

	private static NewOrderItemDto item(ReservationItemType type, Long id, int quantity, String price) {
		return new NewOrderItemDto(type, id, quantity, new BigDecimal(price));
	}

	private static ReservedItemDto reserved(ReservationItemType type, Long id, String name, String price, String cost) {
		return new ReservedItemDto(type, id, name, new BigDecimal(price), cost == null ? null : new BigDecimal(cost));
	}
}
