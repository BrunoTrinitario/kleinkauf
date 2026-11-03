package com.decor.users;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.dtos.dashboard.UserDashboardDto;
import com.decor.users.dtos.platform.PeriodCountDto;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import com.decor.users.services.UserDashboardService;
import com.decor.users.services.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
Panel de usuarios de la tienda contra una base real (H2 en modo PostgreSQL), sin el multi-tenant:
valida las consultas JPQL y que los borrados no cuenten.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:users-dashboard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(UserDashboardService.class)
class UserDashboardServiceTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private UserDashboardService dashboardService;

	// Lo pide el CommandLineRunner de DecorusersApplication
	@MockitoBean
	private UserService userService;

	@Test
	void cuentaTotalActivosYPorRol_sinLosBorrados() throws ApiException {
		LocalDateTime now = LocalDateTime.now();
		user("admin@t.com", now, now.minusDays(1), "ADMINISTRADOR", "EMPLEADO");
		user("empleado@t.com", now, now.minusDays(40), "EMPLEADO");
		user("cliente@t.com", now, null, "CLIENTE");
		User borrado = user("borrado@t.com", now, now, "CLIENTE");
		borrado.setDeletedAt(now);
		em.flush();
		em.clear();

		UserDashboardDto dashboard = dashboardService.dashboard(null, null, null);

		assertEquals(3, dashboard.total());
		// Entro en los ultimos 30 dias: solo el administrador (el borrado no cuenta)
		assertEquals(1, dashboard.active());
		assertEquals(30, dashboard.activeWindowDays());
		assertEquals(Map.of("ADMINISTRADOR", 1L, "EMPLEADO", 2L, "CLIENTE", 1L), dashboard.byRole());
	}

	@Test
	void sinUsuarios_losRolesVanEnCero() throws ApiException {
		UserDashboardDto dashboard = dashboardService.dashboard(null, null, null);

		assertEquals(0, dashboard.total());
		assertEquals(List.of("ADMINISTRADOR", "EMPLEADO", "CLIENTE"), List.copyOf(dashboard.byRole().keySet()));
		assertEquals(30, dashboard.registrations().periods().size());
	}

	@Test
	void altasPorPeriodo_incluyeElUltimoDiaEntero() throws ApiException {
		user("a@t.com", LocalDateTime.of(2026, 10, 5, 0, 0), null, "CLIENTE");
		user("b@t.com", LocalDateTime.of(2026, 10, 6, 23, 59), null, "CLIENTE");
		user("c@t.com", LocalDateTime.of(2026, 10, 12, 10, 0), null, "CLIENTE");
		// Afuera del rango
		user("d@t.com", LocalDateTime.of(2026, 10, 13, 0, 0), null, "CLIENTE");
		em.flush();

		UserDashboardDto dashboard = dashboardService.dashboard("2026-10-05", "2026-10-12", "WEEK");

		// Semanas que empiezan el lunes: 5/10 y 12/10
		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2026, 10, 5), 2),
				new PeriodCountDto(LocalDate.of(2026, 10, 12), 1)), dashboard.registrations().periods());
	}

	@Test
	void periodoInvalido_400() {
		ApiException e = assertThrows(ApiException.class, () -> dashboardService.dashboard("2026-10-10", "2026-10-01", null));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
	}

	private User user(String email, LocalDateTime createdAt, LocalDateTime lastLogin, String... roles) {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPassword("x");
		user.setCreatedAt(createdAt);
		user.setLastLogin(lastLogin);
		em.persist(user);
		for (String type : roles) {
			Role role = new Role();
			role.setType(type);
			role.setUser(user);
			em.persist(role);
		}
		return user;
	}
}
