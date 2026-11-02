package com.decor.sales;

import com.decor.common.dto.stock.ReservationItemType;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.dto.reports.BreakdownDto;
import com.decor.sales.dto.reports.PeriodDto;
import com.decor.sales.dto.reports.ProductReportDto;
import com.decor.sales.dto.reports.ProductRankingDto;
import com.decor.sales.dto.reports.SalesSummaryDto;
import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.services.ReportService;
import com.decor.sales.util.SalesTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
Reportes contra H2: la consulta de ventas y la agregacion. Las fechas se arman en la zona de la tienda
(Argentina) y se guardan en la del servidor, como en produccion: el test no depende de la zona de la maquina.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:reports;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ReportService.class, SalesTime.class})
class ReportServiceTest {

	private static final ZoneId STORE = ZoneId.of("America/Argentina/Buenos_Aires");

	@Autowired
	private TestEntityManager em;

	@Autowired
	private ReportService reportService;

	@Test
	void resumen_porDiaDeLaTienda_conPeriodosVaciosYDesgloses() throws ApiException {
		// Lunes 2/11 a las 22 hs en Argentina: en UTC ya es martes, pero cuenta para el lunes
		persist(sale("AAAA-AAAA", SalesChannel.ONLINE, PaymentMethod.TRANSFERENCIA, OrderStatus.PAGADO, at(2026, 11, 2, 22), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 2, "100.00", "60.00")));
		persist(sale("BBBB-BBBB", SalesChannel.LOCAL, PaymentMethod.EFECTIVO, OrderStatus.ENTREGADO, at(2026, 11, 4, 10), "10.00",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 1, "100.00", "60.00"),
				item(ReservationItemType.BUNDLE, 9L, "Comedor", 1, "50.00", null)));
		// No cuentan: sin pagar, cancelado y fuera del rango
		persist(sale("CCCC-CCCC", SalesChannel.ONLINE, PaymentMethod.TRANSFERENCIA, OrderStatus.PAGO_EN_REVISION, at(2026, 11, 3, 10), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 5, "100.00", "60.00")));
		persist(sale("DDDD-DDDD", SalesChannel.ONLINE, PaymentMethod.TRANSFERENCIA, OrderStatus.CANCELADO, at(2026, 11, 3, 10), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 5, "100.00", "60.00")));
		persist(sale("EEEE-EEEE", SalesChannel.ONLINE, PaymentMethod.TRANSFERENCIA, OrderStatus.PAGADO, at(2026, 11, 5, 0), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 5, "100.00", "60.00")));
		em.flush();
		em.clear();

		SalesSummaryDto summary = reportService.summary("2026-11-02", "2026-11-04", null, null, null);

		assertEquals(2, summary.totals().orders());
		assertEquals(4, summary.totals().units());
		// 200 + (150 - 10)
		assertEquals(0, new BigDecimal("340.00").compareTo(summary.totals().revenue()));
		assertEquals(0, new BigDecimal("10.00").compareTo(summary.totals().discounts()));
		assertEquals(0, new BigDecimal("180.00").compareTo(summary.totals().cost()));
		assertEquals(0, new BigDecimal("160.00").compareTo(summary.totals().profit()));
		assertEquals(1, summary.totals().unitsWithoutCost());

		List<PeriodDto> periods = summary.periods();
		assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4)),
				periods.stream().map(PeriodDto::start).toList());
		assertEquals(List.of(1L, 0L, 1L), periods.stream().map(p -> p.totals().orders()).toList());

		assertEquals(List.of("ONLINE", "LOCAL"), summary.byChannel().stream().map(BreakdownDto::key).toList());
		assertEquals(List.of("EFECTIVO", "TRANSFERENCIA"), summary.byPaymentMethod().stream().map(BreakdownDto::key).toList());
		assertEquals("America/Argentina/Buenos_Aires", summary.zone());
	}

	@Test
	void resumen_porSemanaYMes_yFiltroDeCanal() throws ApiException {
		persist(sale("AAAA-AAAA", SalesChannel.ONLINE, PaymentMethod.TRANSFERENCIA, OrderStatus.PAGADO, at(2026, 10, 30, 12), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 1, "100.00", null)));
		persist(sale("BBBB-BBBB", SalesChannel.LOCAL, PaymentMethod.DEBITO, OrderStatus.PAGADO, at(2026, 11, 3, 12), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 1, "100.00", null)));
		em.flush();
		em.clear();

		// Semanas desde el lunes: 26/10 y 2/11
		SalesSummaryDto weeks = reportService.summary("2026-10-28", "2026-11-04", "week", null, null);
		assertEquals(List.of(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)), weeks.periods().stream().map(PeriodDto::start).toList());

		SalesSummaryDto months = reportService.summary("2026-10-28", "2026-11-04", "MONTH", "local", null);
		assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), months.periods().stream().map(PeriodDto::start).toList());
		assertEquals(List.of(0L, 1L), months.periods().stream().map(p -> p.totals().orders()).toList());
	}

	@Test
	void ranking_porUnidadesFacturadoOGanancia_conElNombreMasReciente() throws ApiException {
		persist(sale("AAAA-AAAA", SalesChannel.ONLINE, PaymentMethod.TRANSFERENCIA, OrderStatus.PAGADO, at(2026, 11, 1, 12), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa vieja", 1, "100.00", "90.00"),
				item(ReservationItemType.PRODUCT, 6L, "Silla", 4, "20.00", "5.00")));
		persist(sale("BBBB-BBBB", SalesChannel.LOCAL, PaymentMethod.EFECTIVO, OrderStatus.ENTREGADO, at(2026, 11, 2, 12), "0",
				item(ReservationItemType.PRODUCT, 5L, "Mesa", 1, "100.00", "90.00"),
				item(ReservationItemType.BUNDLE, 5L, "Combo con el mismo id", 1, "500.00", null)));
		em.flush();
		em.clear();

		ProductReportDto units = reportService.products("2026-11-01", "2026-11-02", null, null, null);
		assertEquals(List.of("Silla", "Mesa", "Combo con el mismo id"), units.items().stream().map(ProductRankingDto::name).toList());
		assertEquals(2, units.items().get(1).units());

		ProductReportDto revenue = reportService.products("2026-11-01", "2026-11-02", null, "revenue", null);
		assertEquals("Combo con el mismo id", revenue.items().get(0).name());
		assertEquals(1, revenue.items().get(0).unitsWithoutCost());

		// Ganancia: silla 60, mesa 20 (el combo sin costo aparece con todo como ganancia, avisado en unitsWithoutCost)
		ProductReportDto profit = reportService.products("2026-11-01", "2026-11-02", null, "profit", 2);
		assertEquals(List.of("Combo con el mismo id", "Silla"), profit.items().stream().map(ProductRankingDto::name).toList());

		ProductReportDto local = reportService.products("2026-11-01", "2026-11-02", "LOCAL", null, null);
		assertEquals(2, local.items().size());
	}

	@Test
	void validaRangoYParametros() {
		assertBadRequest(() -> reportService.summary("2026-11-05", "2026-11-01", null, null, null));
		assertBadRequest(() -> reportService.summary("2025-01-01", "2026-11-01", null, null, null));
		assertBadRequest(() -> reportService.summary("01/11/2026", null, null, null, null));
		assertBadRequest(() -> reportService.summary(null, null, "year", null, null));
		assertBadRequest(() -> reportService.products(null, null, "WEB", null, null));
		assertBadRequest(() -> reportService.products(null, null, null, "price", null));
		assertBadRequest(() -> reportService.products(null, null, null, null, 101));
	}

	@Test
	void sinFechas_losUltimos30Dias() throws ApiException {
		SalesSummaryDto summary = reportService.summary(null, null, null, null, null);
		assertEquals(30, summary.periods().size());
		assertEquals(LocalDate.now(STORE), summary.to());
	}

	private interface Call {
		void run() throws ApiException;
	}

	private static void assertBadRequest(Call call) {
		ApiException e = assertThrows(ApiException.class, call::run);
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
	}

	// Hora de la tienda -> hora del servidor, como lo guarda LocalDateTime.now()
	private static LocalDateTime at(int year, int month, int day, int hour) {
		return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, STORE)
				.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
	}

	private void persist(Order order) {
		em.persist(order);
	}

	private static Order sale(String number, SalesChannel channel, PaymentMethod method, OrderStatus status,
							  LocalDateTime paidAt, String discount, OrderItem... items) {
		Order order = new Order();
		order.setNumber(number);
		order.setChannel(channel);
		order.setPaymentMethod(method);
		order.setDeliveryType(DeliveryType.RETIRO);
		BigDecimal subtotal = BigDecimal.ZERO;
		for (OrderItem item : items) {
			order.addItem(item);
			subtotal = subtotal.add(item.getSubtotal());
		}
		order.setDiscount(new BigDecimal(discount));
		order.setTotal(subtotal.subtract(order.getDiscount()));
		order.changeStatus(status, null, null);
		order.setPaidAt(paidAt);
		order.setCreatedAt(paidAt);
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
