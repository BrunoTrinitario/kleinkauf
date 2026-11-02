package com.decor.product;

import com.decor.common.constants.Headers;
import com.decor.common.dto.RoleDto;
import com.decor.common.security.PermissionArea;
import com.decor.common.security.InternalTokenService;
import com.decor.common.servlet.tenant.TenantFilter;
import com.decor.product.controllers.BundleController;
import com.decor.product.controllers.CatalogController;
import com.decor.product.controllers.InternalStockController;
import com.decor.product.controllers.ProductCsvController;
import com.decor.product.controllers.ProductController;
import com.decor.product.controllers.StockController;
import com.decor.product.dto.csv.ImportResultDto;
import com.decor.product.security.SecurityConfig;
import com.decor.product.services.BundleService;
import com.decor.product.services.CatalogService;
import com.decor.product.services.ProductCsvService;
import com.decor.product.services.ProductService;
import com.decor.product.services.StockReservationService;
import com.decor.product.services.StockService;
import com.decor.product.services.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Permisos por rol con el SecurityConfig y el HeaderAuthFilter reales.
Los roles salen de UserService (mock) segun el header X-User-Id que agrega el gateway.
 */
@WebMvcTest(controllers = {ProductController.class, StockController.class, BundleController.class, ProductCsvController.class, CatalogController.class, InternalStockController.class})
@Import({SecurityConfig.class, SecurityRulesTest.TenantConfig.class})
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
	private ProductService productService;

	@MockitoBean
	private StockService stockService;

	@MockitoBean
	private BundleService bundleService;

	@MockitoBean
	private ProductCsvService productCsvService;

	@MockitoBean
	private CatalogService catalogService;

	@MockitoBean
	private StockReservationService stockReservationService;

	// Lo que users devuelve para EMPLEADO: por defecto solo STOCK; cada test puede configurarlo
	private int empleadoPermissions = PermissionArea.STOCK.bit();

	private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
		when(userService.getUserRolesById(ADMIN)).thenReturn(List.of(new RoleDto("ADMINISTRADOR", PermissionArea.ALL)));
		when(userService.getUserRolesById(EMPLEADO)).thenReturn(List.of(new RoleDto("EMPLEADO", empleadoPermissions)));
		when(userService.getUserRolesById(CLIENTE)).thenReturn(List.of(new RoleDto("CLIENTE", 0)));
		request.header(Headers.FORWARDED_HOST, "tienda1");
		if (userId != null) request.header(Headers.USER_ID, userId);
		return request;
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void catalogoPublico_sinLogin() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/bundles/all"))).andExpect(status().isOk());
	}

	// Los listados del panel muestran el stock exacto: piden algun area del panel (el EMPLEADO por defecto tiene STOCK)
	@Test
	void listadoDelPanel_pideAreaDelPanel() throws Exception {
		for (String path : List.of("/api/v1/products", "/api/v1/products/all")) {
			mockMvc.perform(as(null, get(path))).andExpect(status().isForbidden());
			mockMvc.perform(as(CLIENTE, get(path))).andExpect(status().isForbidden());
			mockMvc.perform(as(EMPLEADO, get(path))).andExpect(status().isOk());
			mockMvc.perform(as(ADMIN, get(path))).andExpect(status().isOk());
		}
	}

	@Test
	void catalogoNuevo_sinLogin() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/catalog/products").param("q", "mesa").param("sort", "price_asc")))
				.andExpect(status().isOk());
		mockMvc.perform(as(null, get("/api/v1/catalog/products/mesa-de-roble"))).andExpect(status().isOk());
		mockMvc.perform(as(null, get("/api/v1/catalog/categories"))).andExpect(status().isOk());
		mockMvc.perform(as(null, get("/api/v1/catalog/bundles"))).andExpect(status().isOk());
	}

	@Test
	void reservasDeStock_soloConTokenInterno() throws Exception {
		String body = "{\"reference\":\"K7QM-4X2P\",\"items\":[{\"type\":\"PRODUCT\",\"id\":5,\"quantity\":1}]}";
		when(internalTokenService.validateInternalToken("valido")).thenReturn(true);

		// Sin token, ni siquiera el administrador
		mockMvc.perform(as(null, json(post("/api/internal/products/reservations"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(post("/api/internal/products/reservations"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(null, json(post("/api/internal/products/reservations"), body)
				.header(Headers.INTERNAL_TOKEN, "falso"))).andExpect(status().isForbidden());

		mockMvc.perform(as(null, json(post("/api/internal/products/reservations"), body)
				.header(Headers.INTERNAL_TOKEN, "valido"))).andExpect(status().isOk());
		mockMvc.perform(as(null, post("/api/internal/products/reservations/K7QM-4X2P/release")
				.header(Headers.INTERNAL_REQUEST, "true")
				.header(Headers.INTERNAL_TOKEN, "valido"))).andExpect(status().isNoContent());
	}

	@Test
	void crearProducto_soloAdministrador() throws Exception {
		String body = "{\"name\":\"Silla\",\"stock\":1}";
		mockMvc.perform(as(CLIENTE, json(post("/api/v1/products"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/products"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(post("/api/v1/products"), body))).andExpect(status().isOk());
	}

	@Test
	void movimientosDeStock_administradorYEmpleado() throws Exception {
		String body = "{\"type\":\"ENTRADA\",\"quantity\":3}";
		mockMvc.perform(as(null, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(CLIENTE, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isCreated());
		mockMvc.perform(as(ADMIN, json(post("/api/v1/products/5/stock/movements"), body))).andExpect(status().isCreated());

		mockMvc.perform(as(CLIENTE, get("/api/v1/products/5/stock/movements"))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/5/stock/movements"))).andExpect(status().isOk());
	}

	@Test
	void stockBajo_administradorYEmpleado() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/products/stock/low"))).andExpect(status().isForbidden());
		mockMvc.perform(as(CLIENTE, get("/api/v1/products/stock/low"))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/stock/low"))).andExpect(status().isOk());
	}

	@Test
	void costoYMargen_soloAdministrador() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/products/5/pricing"))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/5/pricing"))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, get("/api/v1/products/5/pricing"))).andExpect(status().isOk());

		String body = "{\"price\":100,\"cost\":60}";
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/products/5/pricing"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/products/5/pricing"), body))).andExpect(status().isOk());
	}

	@Test
	void destacados_listaPublicaYMarcarSoloAdministrador() throws Exception {
		mockMvc.perform(as(null, get("/api/v1/products/featured"))).andExpect(status().isOk());

		String body = "{\"featured\":true,\"order\":1}";
		mockMvc.perform(as(null, json(put("/api/v1/products/5/featured"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/products/5/featured"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/products/5/featured"), body))).andExpect(status().isOk());
	}

	@Test
	void importarYExportar_soloAdministrador() throws Exception {
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/export"))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, get("/api/v1/products/export"))).andExpect(status().isOk());

		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/import/template"))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, get("/api/v1/products/import/template"))).andExpect(status().isOk());

		when(productCsvService.importCsv(any())).thenReturn(new ImportResultDto(1, 0, List.of()));
		MockMultipartFile file = new MockMultipartFile("file", "p.csv", "text/csv", "nombre\r\nMesa\r\n".getBytes());
		mockMvc.perform(as(EMPLEADO, multipart("/api/v1/products/import").file(file))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, multipart("/api/v1/products/import").file(file))).andExpect(status().isOk());
	}

	@Test
	void importarConErrores_responde400ConLosErrores() throws Exception {
		when(productCsvService.importCsv(any())).thenReturn(
				new ImportResultDto(0, 0, List.of(new ImportResultDto.RowError(2, "The name is required"))));
		MockMultipartFile file = new MockMultipartFile("file", "p.csv", "text/csv", "nombre\r\n;\r\n".getBytes());

		mockMvc.perform(as(ADMIN, multipart("/api/v1/products/import").file(file)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].row").value(2));
	}

	@Test
	void precioDelCombo_soloAdministrador() throws Exception {
		String body = "{\"price\":90000}";
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/bundles/1/price"), body))).andExpect(status().isForbidden());
		mockMvc.perform(as(ADMIN, json(put("/api/v1/bundles/1/price"), body))).andExpect(status().isOk());
	}

	// Permisos configurados por el administrador para EMPLEADO

	@Test
	void empleadoConPrecios_veYEditaPreciosPeroNoProductos() throws Exception {
		empleadoPermissions = PermissionArea.PRECIOS.bit();

		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/5/pricing"))).andExpect(status().isOk());
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/products/5/pricing"), "{\"price\":100}"))).andExpect(status().isOk());
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/products/5"), "{\"name\":\"x\"}"))).andExpect(status().isForbidden());
		// Ya no tiene STOCK
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/stock/low"))).andExpect(status().isForbidden());
	}

	@Test
	void empleadoConProductos_editaProductosYDestacados() throws Exception {
		empleadoPermissions = PermissionArea.PRODUCTOS.bit();

		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/products"), "{\"name\":\"Silla\"}"))).andExpect(status().isOk());
		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/products/5/featured"), "{\"featured\":true}"))).andExpect(status().isOk());
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/5/pricing"))).andExpect(status().isForbidden());
	}

	@Test
	void csv_pideProductosYPrecios() throws Exception {
		empleadoPermissions = PermissionArea.PRODUCTOS.bit();
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/export"))).andExpect(status().isForbidden());

		empleadoPermissions = PermissionArea.PRECIOS.bit();
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/export"))).andExpect(status().isForbidden());

		empleadoPermissions = PermissionArea.PRODUCTOS.bit() | PermissionArea.PRECIOS.bit();
		mockMvc.perform(as(EMPLEADO, get("/api/v1/products/export"))).andExpect(status().isOk());
	}

	@Test
	void empleadoConCombos_editaCombosYSuPrecio() throws Exception {
		empleadoPermissions = PermissionArea.COMBOS.bit();

		mockMvc.perform(as(EMPLEADO, json(put("/api/v1/bundles/1/price"), "{\"price\":90000}"))).andExpect(status().isOk());
		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/bundles"), "{\"name\":\"Combo\"}"))).andExpect(status().isOk());
		mockMvc.perform(as(EMPLEADO, json(post("/api/v1/products"), "{\"name\":\"Silla\"}"))).andExpect(status().isForbidden());
	}

	@Test
	void clienteSinPermisos_noAccedeAlPanel() throws Exception {
		mockMvc.perform(as(CLIENTE, get("/api/v1/products/stock/low"))).andExpect(status().isForbidden());
		mockMvc.perform(as(CLIENTE, json(post("/api/v1/bundles"), "{\"name\":\"Combo\"}"))).andExpect(status().isForbidden());
		// El catalogo sigue siendo publico
		mockMvc.perform(as(CLIENTE, get("/api/v1/catalog/products"))).andExpect(status().isOk());
	}
}
