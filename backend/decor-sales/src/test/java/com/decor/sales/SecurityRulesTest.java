package com.decor.sales;

import com.decor.common.constants.Headers;
import com.decor.common.dto.RoleDto;
import com.decor.common.security.InternalTokenService;
import com.decor.common.security.PermissionArea;
import com.decor.common.servlet.tenant.TenantFilter;
import com.decor.sales.config.SalesExceptionHandler;
import com.decor.sales.controllers.AdminSalesController;
import com.decor.sales.controllers.OrderController;
import com.decor.sales.dto.PriceChangeDto;
import com.decor.sales.entities.PaymentReceipt;
import com.decor.sales.security.SecurityConfig;
import com.decor.sales.services.AdminOrderService;
import com.decor.sales.services.OrderService;
import com.decor.sales.services.PricesChangedException;
import com.decor.sales.services.SalesSettingsService;
import com.decor.sales.services.UserService;
import com.decor.common.dto.stock.ReservationItemType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Permisos por rol con el SecurityConfig y el HeaderAuthFilter reales.
Los roles salen de UserService (mock) segun el header X-User-Id que agrega el gateway.
 */
@WebMvcTest(controllers = {OrderController.class, AdminSalesController.class})
@Import({SecurityConfig.class, SalesExceptionHandler.class, SecurityRulesTest.TenantConfig.class})
class SecurityRulesTest {

	private static final long ADMIN = 1L;
	private static final long EMPLEADO = 2L;
	private static final long CLIENTE = 3L;

	@TestConfiguration
	static class TenantConfig {
		@Bean
		TenantFilter tenantFilter() {
			return new TenantFilter(true);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private InternalTokenService internalTokenService;

	@MockitoBean
	private OrderService orderService;

	@MockitoBean
	private AdminOrderService adminOrderService;

	@MockitoBean
	private SalesSettingsService settingsService;

	private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
		when(userService.getUserRolesById(ADMIN)).thenReturn(List.of(new RoleDto("ADMINISTRADOR", PermissionArea.ALL)));
		when(userService.getUserRolesById(EMPLEADO)).thenReturn(List.of(new RoleDto("EMPLEADO", PermissionArea.ALL - PermissionArea.USUARIOS.bit())));
		when(userService.getUserRolesById(CLIENTE)).thenReturn(List.of(new RoleDto("CLIENTE", 0)));
		request.header(Headers.FORWARDED_HOST, "tienda1");
		if (userId != null) request.header(Headers.USER_ID, userId);
		return request;
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void checkout_publicoParaInvitadosYLogueados() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/sales/checkout"))).andExpect(status().isOk());
		mockMvc.perform(as(null, json(post("/api/v1/sales/orders"), "{}"))).andExpect(status().isCreated());
		mockMvc.perform(as(CLIENTE, json(post("/api/v1/sales/orders"), "{}"))).andExpect(status().isCreated());
		mockMvc.perform(as(null, json(post("/api/v1/sales/orders/lookup"), "{}"))).andExpect(status().isOk());
	}

	@Test
	void misPedidos_pideLogin() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/sales/orders/mine"))).andExpect(status().isForbidden());
		mockMvc.perform(as(CLIENTE, get("/api/v1/sales/orders/mine"))).andExpect(status().isOk());
	}

	@Test
	void panelDeVentas_soloAdministrador() throws Exception {
		// El empleado no gestiona ventas aunque tenga todas las areas de productos
		for (Long user : new Long[]{null, CLIENTE, EMPLEADO}) {
			mockMvc.perform(as(user, get("/api/v1/sales/admin/orders"))).andExpect(status().isForbidden());
			mockMvc.perform(as(user, get("/api/v1/sales/admin/orders/K7QM-4X2P"))).andExpect(status().isForbidden());
			mockMvc.perform(as(user, get("/api/v1/sales/admin/settings"))).andExpect(status().isForbidden());
			mockMvc.perform(as(user, json(put("/api/v1/sales/admin/settings"), "{}"))).andExpect(status().isForbidden());
		}
		mockMvc.perform(as(ADMIN, get("/api/v1/sales/admin/orders"))).andExpect(status().isOk());
		mockMvc.perform(as(ADMIN, get("/api/v1/sales/admin/orders/K7QM-4X2P"))).andExpect(status().isOk());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/sales/admin/settings"), "{}"))).andExpect(status().isOk());
	}

	@Test
	void comprobanteYCancelacion_publicos() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "c.png", "image/png", new byte[]{1});
		mockMvc.perform(as(null, multipart("/api/v1/sales/orders/K7QM-4X2P/receipt").file(file).param("email", "ana@mail.com")))
				.andExpect(status().isOk());
		mockMvc.perform(as(null, json(post("/api/v1/sales/orders/K7QM-4X2P/cancel"), "{\"email\":\"ana@mail.com\"}")))
				.andExpect(status().isOk());
	}

	@Test
	void ventasDelLocalEstadosYComprobantes_soloAdministrador() throws Exception {
		PaymentReceipt receipt = new PaymentReceipt();
		receipt.setPath("tienda1/K7QM-4X2P/a.pdf");
		receipt.setContentType("application/pdf");
		when(adminOrderService.receipt(any(), any())).thenReturn(
				new AdminOrderService.ReceiptFile(receipt, new ByteArrayResource("%PDF-1.7".getBytes())));

		for (Long user : new Long[]{null, CLIENTE, EMPLEADO}) {
			mockMvc.perform(as(user, json(post("/api/v1/sales/admin/orders"), "{}"))).andExpect(status().isForbidden());
			mockMvc.perform(as(user, json(put("/api/v1/sales/admin/orders/K7QM-4X2P/status"), "{}"))).andExpect(status().isForbidden());
			mockMvc.perform(as(user, get("/api/v1/sales/admin/orders/K7QM-4X2P/receipts/1"))).andExpect(status().isForbidden());
		}
		mockMvc.perform(as(ADMIN, json(post("/api/v1/sales/admin/orders"), "{}"))).andExpect(status().isCreated());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/sales/admin/orders/K7QM-4X2P/status"), "{\"status\":\"PAGADO\"}")))
				.andExpect(status().isOk());
		mockMvc.perform(as(ADMIN, get("/api/v1/sales/admin/orders/k7qm-4x2p/receipts/1")))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"comprobante-K7QM-4X2P-1.pdf\""));
	}

	@Test
	void sinTenant_404() throws Exception {
		mockMvc.perform(get("/api/v1/sales/checkout")).andExpect(status().isNotFound());
	}

	@Test
	void precioCambiado_409ConLosPreciosNuevos() throws Exception {
		when(orderService.create(any())).thenThrow(new PricesChangedException(List.of(
				new PriceChangeDto(ReservationItemType.PRODUCT, 5L, "Mesa", new BigDecimal("100.00"), new BigDecimal("120.00")))));

		mockMvc.perform(as(null, json(post("/api/v1/sales/orders"), "{}")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.items[0].name").value("Mesa"))
				.andExpect(jsonPath("$.items[0].currentPrice").value(120.00));
	}
}
